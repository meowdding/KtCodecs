package me.owdding.ktcodecs

import org.intellij.lang.annotations.Language

internal object BuiltinCodecClasses {

    val PACKAGE_IDENTIFIER = "/*%%PACKAGE%%*/"
    val CODECS_IDENTIFIER = "/*%%CODEC_THINGY%%*/"

    @Language("kotlin")
    val ENUM_CODEC = """
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
    val CODEC_UTILS = """
        package $PACKAGE_IDENTIFIER
        
        internal object CodecUtils {
        
            val UUID_CODEC = com.mojang.serialization.Codec.STRING.xmap(
                { java.util.UUID.fromString(it) },
                { it.toString() }
            )
        
            fun <T> compact(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<List<T>> =
                com.mojang.serialization.Codec.either(codec.listOf(), codec).xmap(
                    { it.map({ it }, { listOf<T>(it) }) },
                    { if (it.size == 1) com.mojang.datafixers.util.Either.right(it[0]) else com.mojang.datafixers.util.Either.left(it) }
                )
        
            fun <T> compactMutableSet(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<MutableSet<T>> =
                compact(codec).xmap({ it.toMutableSet() }, { it.toList() })
        
            fun <T> mutableSet(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<MutableSet<T>> =
                codec.listOf().xmap({ it.toMutableSet() }, { it.toList() })
        
            fun <T> compactSet(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<Set<T>> =
                compact(codec).xmap({ it.toSet() }, { it.toList() })
        
            fun <T> set(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<Set<T>> =
                codec.listOf().xmap({ it.toSet() }, { it.toList() })
        
            fun <T> compactList(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<List<T>> =
                compact(codec).xmap({ it.toMutableList() }, { it })
        
            fun <T> list(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<List<T>> =
                codec.listOf().xmap({ it.toMutableList() }, { it })
        
            fun <T> compactMutableList(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<MutableList<T>> =
                compact(codec).xmap({ it.toMutableList() }, { it })
        
            fun <T> mutableList(codec: com.mojang.serialization.Codec<T>): com.mojang.serialization.Codec<MutableList<T>> =
                codec.listOf().xmap({ it.toMutableList() }, { it })
        
            fun <A, B> map(
                key: com.mojang.serialization.Codec<A>,
                value: com.mojang.serialization.Codec<B>
            ): com.mojang.serialization.Codec<MutableMap<A, B>> =
                com.mojang.serialization.Codec.unboundedMap(key, value).xmap({ it.toMutableMap() }, { it })
        
            fun <T> lazyMapCodec(init: () -> com.mojang.serialization.MapCodec<T>): com.mojang.serialization.MapCodec<T> {
                return com.mojang.serialization.MapCodec.recursive(init.toString()) { init() }
            }
        
            fun <T> toLazy(codec: com.mojang.serialization.MapCodec<T>): com.mojang.serialization.MapCodec<Lazy<T>> {
                return codec.xmap({ lazyOf(it) }, { it.value })
            }
        }
    """.trimIndent()

    @Language("kotlin")
    val DISPATCH_HELPER = """
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