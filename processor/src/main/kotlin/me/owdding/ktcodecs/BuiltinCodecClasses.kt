package me.owdding.ktcodecs

import org.intellij.lang.annotations.Language

internal object BuiltinCodecClasses {

    val PACKAGE_IDENTIFIER = "/*%%PACKAGE%%*/"
    val CODECS_IDENTIFIER = "/*%%CODEC_THINGY%%*/"

    @Language("kotlin")
    val ENUM_CODEC = """
        @file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
        package $PACKAGE_IDENTIFIER
        
        internal class EnumCodec<T> private constructor(private val codec: com.mojang.serialization.Codec<T>) : com.mojang.serialization.Codec<T> {
        
            override fun <T1 : Any?> encode(input: T, ops: com.mojang.serialization.DynamicOps<T1>?, prefix: T1): com.mojang.serialization.DataResult<T1> = codec.encode(input, ops, prefix)
            override fun <T1 : Any?> decode(ops: com.mojang.serialization.DynamicOps<T1>?, input: T1): com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<T, T1>> = codec.decode(ops, input)
        
            companion object {
        
                fun <T> forKCodec(constants: Array<T>): EnumCodec<T> =
                    EnumCodec(com.mojang.serialization.Codec.withAlternative(constantCodec(constants), intCodec(constants)))
        
                private fun <T> intCodec(constants: Array<T>): com.mojang.serialization.Codec<T> {
                    return com.mojang.serialization.Codec.INT.flatXmap(
                        { ordinal: Int ->
                            if (ordinal >= 0 && ordinal < constants.size) {
                                return@flatXmap com.mojang.serialization.DataResult.success<T>(constants[ordinal])
                            }
                            com.mojang.serialization.DataResult.error { "Unknown enum ordinal: ${'$'}ordinal" }
                        },
                        { value: T -> com.mojang.serialization.DataResult.success((value as Enum<*>).ordinal) },
                    )
                }
        
                private fun <T> constantCodec(constants: Array<T>): com.mojang.serialization.Codec<T> = com.mojang.serialization.Codec.STRING.flatXmap(
                    { name: String ->
                        runCatching {
                            com.mojang.serialization.DataResult.success(constants.first { (it as Enum<*>).name.equals(name, true) })
                        }.getOrElse {
                            com.mojang.serialization.DataResult.error { "Unknown enum name: ${'$'}name" }
                        }
                    },
                    { value: T -> com.mojang.serialization.DataResult.success((value as Enum<*>).name) },
                )
            }
        }
    """.trimIndent()

    @Language("kotlin")
    val ALIAS_CODEC = """
        @file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
        package $PACKAGE_IDENTIFIER

        import com.mojang.serialization.Codec
        import com.mojang.serialization.DataResult
        import com.mojang.serialization.DynamicOps
        import com.mojang.serialization.MapCodec
        import com.mojang.serialization.MapLike
        import com.mojang.serialization.RecordBuilder
        import java.util.stream.Stream
        
        internal data class AliasMapCodec<V>(val keys: List<String>, val codec: Codec<V>) : MapCodec<V>() {
            override fun <T : Any> keys(ops: DynamicOps<T>): Stream<T> = keys.stream().map { ops.createString(it) }
        
            override fun <T : Any> decode(
                ops: DynamicOps<T>,
                map: MapLike<T>,
            ): DataResult<V> {
                val values = keys.mapNotNull {
                    map.get(it)?.let { value -> it to value }
                }
        
                if (values.size > 1) return DataResult.error { "Found multiple keys [${'$'}{values.joinToString(separator = ",") { (first) -> first }}], expected one!" }
                return codec.parse(ops, values.firstOrNull()?.second ?: return DataResult.error { "Unable to find any of ${'$'}{keys.joinToString(", ")}" })
            }
        
            override fun <T : Any> encode(
                value: V,
                ops: DynamicOps<T>,
                prefix: RecordBuilder<T>,
            ): RecordBuilder<T> {
                return prefix.add(keys.first(), codec.encodeStart(ops, value))
            }
        }
    """.trimIndent()

    @Language("kotlin")
    val OPTIONAL_ALIAS_CODEC = """
        @file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
        package $PACKAGE_IDENTIFIER

        import com.mojang.serialization.Codec
        import com.mojang.serialization.DataResult
        import com.mojang.serialization.DynamicOps
        import com.mojang.serialization.MapCodec
        import com.mojang.serialization.MapLike
        import com.mojang.serialization.RecordBuilder
        import java.util.stream.Stream
        import java.util.Optional
        
        internal data class OptionalAliasMapCodec<V : Any>(val keys: List<String>, val lenient: Boolean, val codec: Codec<V>) : MapCodec<Optional<V>>() {
            override fun <T : Any> keys(ops: DynamicOps<T>): Stream<T> = keys.stream().map { ops.createString(it) }
        
            override fun <T : Any> decode(
                ops: DynamicOps<T>,
                map: MapLike<T>,
            ): DataResult<Optional<V>> {
                val values = keys.mapNotNull {
                    map.get(it)?.let { value -> it to value }
                }
        
                if (values.isEmpty()) return DataResult.success(Optional.empty())
                if (values.size > 1) return DataResult.error { "Found multiple keys [${'$'}{values.joinToString(separator = ",") { (first) -> first }}], expected exactly one!" }
        
                val result = codec.parse(ops, values.firstOrNull()?.second ?: return DataResult.error { "Unable to find any of ${'$'}{keys.joinToString(", ")}" })
                if (result.isError && lenient) return DataResult.success(Optional.empty())
        
                return result.map(Optional<V>::of).setPartial(result.resultOrPartial())
            }
        
            override fun <T : Any> encode(
                value: Optional<V>,
                ops: DynamicOps<T>,
                prefix: RecordBuilder<T>,
            ): RecordBuilder<T> {
                if (value.isEmpty) return prefix
                return prefix.add(keys.first(), codec.encodeStart(ops, value.get()))
            }
        }

    """.trimIndent()

    @Language("kotlin")
    val CODEC_UTILS = """
        @file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
        package $PACKAGE_IDENTIFIER
        
        import com.mojang.serialization.Codec
        import java.util.EnumMap
        import java.util.EnumSet

        internal object CodecUtils {

            val UUID_CODEC = Codec.STRING.xmap(
                { java.util.UUID.fromString(it) },
                { it.toString() }
            )

            val JSON_ELEMENT_CODEC = Codec.PASSTHROUGH.xmap(
                { it.convert(com.mojang.serialization.JsonOps.INSTANCE).value },
                { com.mojang.serialization.Dynamic(com.mojang.serialization.JsonOps.INSTANCE, it) }
            )

            private inline fun <reified T> codec(): Codec<T> = $CODECS_IDENTIFIER.getCodec<T>()

            inline fun <reified T> set(): Codec<Set<T>> = set(codec<T>())
            inline fun <reified T> compactSet(): Codec<Set<T>> = compactSet(codec<T>())
            inline fun <reified T> mutableSet(): Codec<MutableSet<T>> = mutableSet(codec<T>())
            inline fun <reified T> compactMutableSet(): Codec<MutableSet<T>> = compactMutableSet(codec<T>())

            inline fun <reified E : Enum<E>> enumSet(): Codec<EnumSet<E>> = enumSet(codec<E>())
            inline fun <reified E : Enum<E>> compactEnumSet(): Codec<EnumSet<E>> = compactEnumSet(codec<E>())

            inline fun <reified T> list(): Codec<List<T>> = list(codec<T>())
            inline fun <reified T> compactList(): Codec<List<T>> = compactList(codec<T>())
            inline fun <reified T> mutableList(): Codec<MutableList<T>> = mutableList(codec<T>())
            inline fun <reified T> compactMutableList(): Codec<MutableList<T>> = compactMutableList(codec<T>())

            inline fun <reified K, reified V> map(): Codec<MutableMap<K, V>> = map(codec<K>(), codec<V>())
            inline fun <reified E : Enum<E>, reified V> enumMap(): Codec<EnumMap<E, V>> = enumMap(codec<E>(), codec<V>())

            inline fun <reified L, reified R> either(): Codec<com.mojang.datafixers.util.Either<L, R>> = Codec.either(codec<L>(), codec<R>())


            fun <T> compact(codec: Codec<T>): Codec<List<T>> =
                Codec.either(codec.listOf(), codec).xmap(
                    { it.map({ it }, { listOf<T>(it) }) },
                    { if (it.size == 1) com.mojang.datafixers.util.Either.right(it[0]) else com.mojang.datafixers.util.Either.left(it) }
                )

            fun <T> compactMutableSet(codec: Codec<T>): Codec<MutableSet<T>> =
                compact(codec).xmap({ it.toMutableSet() }, { it.toList() })

            fun <T> mutableSet(codec: Codec<T>): Codec<MutableSet<T>> =
                codec.listOf().xmap({ it.toMutableSet() }, { it.toList() })

            fun <T> compactSet(codec: Codec<T>): Codec<Set<T>> =
                compact(codec).xmap({ it.toSet() }, { it.toList() })

            fun <T> set(codec: Codec<T>): Codec<Set<T>> =
                codec.listOf().xmap({ it.toSet() }, { it.toList() })

            inline fun <reified E : Enum<E>> compactEnumSet(codec: Codec<E>): Codec<EnumSet<E>> =
                compactEnumSet(E::class.java, codec)

            fun <E : Enum<E>> compactEnumSet(clazz: Class<E>, codec: Codec<E>): Codec<EnumSet<E>> =
                compact(codec).xmap({ EnumSet.noneOf(clazz).apply { addAll(it) } }, { it.toList() })

            inline fun <reified E : Enum<E>> enumSet(codec: Codec<E>): Codec<EnumSet<E>> =
                enumSet(E::class.java, codec)

            fun <E : Enum<E>> enumSet(clazz: Class<E>, codec: Codec<E>): Codec<EnumSet<E>> =
                codec.listOf().xmap({ EnumSet.noneOf(clazz).apply { addAll(it) } }, { it.toList() })


            fun <T> compactList(codec: Codec<T>): Codec<List<T>> =
                compact(codec).xmap({ it.toMutableList() }, { it })

            fun <T> list(codec: Codec<T>): Codec<List<T>> =
                codec.listOf().xmap({ it.toMutableList() }, { it })

            fun <T> compactMutableList(codec: Codec<T>): Codec<MutableList<T>> =
                compact(codec).xmap({ it.toMutableList() }, { it })

            fun <T> mutableList(codec: Codec<T>): Codec<MutableList<T>> =
                codec.listOf().xmap({ it.toMutableList() }, { it })

            fun <A, B> map(
                key: Codec<A>,
                value: Codec<B>
            ): Codec<MutableMap<A, B>> =
                Codec.unboundedMap(key, value).xmap({ it.toMutableMap() }, { it })


            inline fun <reified E : Enum<E>, B> enumMap(
                key: Codec<E>,
                value: Codec<B>,
            ): Codec<EnumMap<E, B>> = enumMap(E::class.java, key, value)

            fun <E : Enum<E>, B> enumMap(
                clazz: Class<E>,
                key: Codec<E>,
                value: Codec<B>,
            ): Codec<EnumMap<E, B>> {
                return Codec.unboundedMap(key, value).xmap({ EnumMap<E, B>(clazz).apply { putAll(it) } }, { it })
            }

            fun <T> lazyMapCodec(init: () -> com.mojang.serialization.MapCodec<T>): com.mojang.serialization.MapCodec<T> {
                return com.mojang.serialization.MapCodec.recursive(init.toString()) { init() }
            }

            fun <T> toLazy(codec: com.mojang.serialization.MapCodec<T>): com.mojang.serialization.MapCodec<Lazy<T>> {
                return codec.xmap({ lazyOf(it) }, { it.value })
            }
            
            fun longRange(min: Long, max: Long): Codec<Long> {
               val checker = Codec.checkRange(min, max)
               return Codec.LONG.flatXmap(checker, checker)
            }

        }
    """.trimIndent()

    @Language("kotlin")
    val DISPATCH_HELPER = """
        @file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
        package $PACKAGE_IDENTIFIER
        
        internal interface DispatchHelper<T: Any> {
            val codec: com.mojang.serialization.MapCodec<out T>
                get() = codec(type)
            val type: kotlin.reflect.KClass<out T>
            val name: String
            val id: String
                get() = name
        
            private fun <T: Any> codec(type: kotlin.reflect.KClass<T>): com.mojang.serialization.MapCodec<T> {
                return $CODECS_IDENTIFIER.getMapCodec(type.java) as com.mojang.serialization.MapCodec<T>
            }
        }

    """.trimIndent()
}
