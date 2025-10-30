package me.owdding.ktcodecs.generators

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import me.owdding.kotlinpoet.CodeBlock
import me.owdding.kotlinpoet.KModifier
import me.owdding.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import me.owdding.kotlinpoet.PropertySpec
import me.owdding.kotlinpoet.asClassName
import me.owdding.kotlinpoet.asTypeName
import me.owdding.kotlinpoet.ksp.toClassName
import me.owdding.kotlinpoet.ksp.toClassNameOrNull
import me.owdding.kotlinpoet.ksp.toTypeName
import me.owdding.ktcodecs.*
import me.owdding.ktcodecs.IntRange
import me.owdding.ktcodecs.LongRange
import me.owdding.ktcodecs.generators.RecordCodecGenerator.component1
import me.owdding.ktcodecs.generators.RecordCodecGenerator.component2
import me.owdding.ktcodecs.utils.*
import me.owdding.ktcodecs.utils.AnnotationUtils.getAnnotation
import me.owdding.ktcodecs.utils.AnnotationUtils.getAnnotationInstance
import me.owdding.ktcodecs.utils.AnnotationUtils.getField
import me.owdding.ktcodecs.utils.AnnotationUtils.resolveClassName
import org.jetbrains.annotations.Range
import java.util.*

internal object RecordCodecGenerator {

    lateinit var logger: KSPLogger
    lateinit var builtinCodec: BuiltinCodecs
    private const val MAX_PARAMETERS = 16

    private fun isValid(
        parameter: KSValueParameter,
        logger: KSPLogger,
        builtinCodecs: BuiltinCodecs,
        declaration: KSClassDeclaration,
    ): Boolean = runCatching {
        val ksType = parameter.type.resolve()
        val name = parameter.name!!.asString()
        if (parameter.isVararg) {
            logger.error("parameter $name is a vararg")
        } else if (parameter.hasDefault && ksType.isMarkedNullable) {
            logger.error("parameter $name is nullable and has a default value")
        } else {
            val isMap = ksType.starProjection().toTypeName() == Map::class.asTypeName() || ksType.starProjection()
                .toTypeName() == MutableMap::class.asTypeName()
            if (isMap) {
                val keyType = ksType.arguments.getRef(0).resolve()
                val type = keyType.toTypeName().copy(false)
                if (Modifier.ENUM !in keyType.declaration.modifiers && !builtinCodecs.isStringType(type)) {
                    logger.error("parameter $name is a map with a key type that is not a string: $type")
                    return@runCatching false
                }
            }
            return@runCatching true
        }
        return@runCatching false
    }.onFailure {
        logger.error("Failed to validate record codec parameter ${parameter.name} for ${declaration.location}")
    }.getOrThrow()

    fun isValid(declaration: KSAnnotated?, logger: KSPLogger, builtinCodecs: BuiltinCodecs): Boolean {
        if (declaration !is KSClassDeclaration) {
            logger.error("Declaration is not a class")
        } else if (declaration.modifiers.contains(Modifier.INLINE)) {
            logger.error("@GenerateCodec can only be applied to non-inline classes")
        } else if (!declaration.isPublic() && !declaration.isInternal()) {
            logger.error("@GenerateCodec can only be applied to public or internal classes")
        } else if (Modifier.DATA !in declaration.modifiers) {
            logger.error("@GenerateCodec can only be applied to data classes")
        } else if (declaration.primaryConstructor == null) {
            logger.error("@GenerateCodec can only be applied to classes with a primary constructor")
        } else if (declaration.primaryConstructor!!.parameters.isEmpty()) {
            logger.error("@GenerateCodec can only be applied to classes with a primary constructor that has parameters")
        } else if (declaration.primaryConstructor!!.parameters.size > MAX_PARAMETERS) {
            logger.error(
                "@GenerateCodec can only be applied to classes with a primary constructor that has at most $MAX_PARAMETERS parameters",
            )
        } else if (!declaration.primaryConstructor!!.parameters.all {
                isValid(
                    it,
                    logger,
                    builtinCodecs,
                    declaration
                )
            }) {
            logger.error(
                "@GenerateCodec can only be applied to classes with a primary constructor that has valid parameters, view the error above for more information",
            )
        } else {
            return true
        }
        return false
    }

    private fun CodeLineBuilder.addUtil(name: String, isCompact: Boolean, parameters: CodeLineBuilder.() -> Unit) {
        if (isCompact) {
            add("CodecUtils.compact${name.replaceFirstChar(Char::uppercase)}(")
        } else {
            add("CodecUtils.${name}(")
        }

        this.parameters()
        add(")")
    }

    private fun CodeLineBuilder.addCodec(type: KSType, isUnnamed: Boolean = false, isCompact: Boolean = false) {
        if (isUnnamed) {
            add("getMapCodec<%T>()", type.toTypeName().copy(nullable = false))
            return
        }

        when (type.resolveClassName()) {
            LAZY -> {
                add("getLazyCodec<%T>()", type.arguments[0].type!!.resolve().toTypeName())
            }

            List::class.asClassName() -> {
                addUtil("list", isCompact) {
                    addCodec(type.arguments[0].type!!.resolve())
                }
            }

            MUTABLE_LIST -> {
                addUtil("mutableList", isCompact) {
                    addCodec(type.arguments[0].type!!.resolve())
                }
            }

            MUTABLE_SET -> {
                addUtil("mutableSet", isCompact) {
                    addCodec(type.arguments[0].type!!.resolve())
                }
            }


            Set::class.asClassName() -> {
                addUtil("set", isCompact) {
                    addCodec(type.arguments[0].type!!.resolve())
                }
            }

            Map::class.asClassName() -> {
                add("%T.unboundedMap(", CODEC_TYPE)
                addCodec(type.arguments[0].type!!.resolve())
                add(", ")
                addCodec(type.arguments[1].type!!.resolve())
                add(")")
            }

            MUTABLE_MAP -> {
                add("CodecUtils.map(")
                addCodec(type.arguments[0].type!!.resolve())
                add(", ")
                addCodec(type.arguments[1].type!!.resolve())
                add(")")
            }

            EnumMap::class.asClassName() -> {
                add("CodecUtils.enumMap(")
                add("${type.arguments[0].type!!.resolveClassName().canonicalName}::class.java")
                add(", ")
                addCodec(type.arguments[0].type!!.resolve())
                add(", ")
                addCodec(type.arguments[1].type!!.resolve())
                add(")")
            }

            EnumSet::class.asClassName() -> {
                addUtil("enumSet", isCompact) {
                    add("${type.arguments[0].type!!.resolveClassName().canonicalName}::class.java")
                    add(", ")
                    addCodec(type.arguments[0].type!!.resolve())
                }
            }

            EITHER_TYPE -> {
                add("%T.either(", CODEC_TYPE)
                addCodec(type.arguments[0].type!!.resolve())
                add(", ")
                addCodec(type.arguments[1].type!!.resolve())
                add(")")
            }

            else -> {
                add("getCodec<%T>()", type.toTypeName().copy(nullable = false))
            }
        }
    }

    private fun CodeLineBuilder.addIntRange(min: Int, max: Int) {
        this.add("Codec.intRange($min, $max)")
    }

    private fun CodeLineBuilder.addLongRange(min: Long, max: Long) {
        this.add("CodecUtils.longRange($min, $max)")
    }

    private fun CodeLineBuilder.addDoubleRange(min: Double, max: Double) {
        this.add("Codec.doubleRange($min, $max)")
    }

    private fun CodeLineBuilder.addFloatRange(min: Float, max: Float) {
        this.add("Codec.floatRange(${min}f, ${max}f)")
    }

    @OptIn(KspExperimental::class)
    private fun CodeBlock.Builder.createEntry(
        parameter: KSValueParameter,
        declaration: KSClassDeclaration,
        lazy: Boolean,
    ): Pair<String, Type> {
        val fieldName = parameter.getField<FieldName, String>("value") ?: parameter.name!!.asString()
        val namedCodec = parameter.getField<NamedCodec, String>("name")
        val isCompact = parameter.getAnnotation<Compact>() != null
        val isInlined = parameter.getAnnotation<Inline>() != null
        val isLenient = parameter.getAnnotation<Lenient>() != null

        val name = parameter.name!!.asString()
        val nullable = parameter.type.resolve().isMarkedNullable
        val ksType = parameter.type.resolve()

        val builder = CodeLineBuilder()

        if (namedCodec != null) {
            if (isCompact) error("Compact and NamedCodec cannot be used together")
            try {
                builder.add(builtinCodec.namedCodecs[namedCodec]!!)
            } catch (e: NullPointerException) {
                throw RuntimeException("Required unknown named codec $namedCodec", e)
            }
        } else {
            val type = parameter.type.resolve().toClassNameOrNull()
            when {
                parameter.type.isAnnotationPresent(Range::class) -> {
                    val (from, to) = parameter.type.getAnnotationInstance<Range>()
                    when (type) {
                        INT -> builder.addIntRange(
                            from.coerceAtLeast(Int.MIN_VALUE.toLong()).toInt(),
                            to.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                        )

                        DOUBLE -> builder.addDoubleRange(
                            from.coerceAtLeast(Double.MIN_VALUE.toLong()).toDouble(),
                            to.coerceAtMost(Double.MAX_VALUE.toLong()).toDouble()
                        )

                        LONG -> builder.addLongRange(from, to)
                        FLOAT -> builder.addFloatRange(
                            from.coerceAtLeast(Float.MIN_VALUE.toLong()).toFloat(),
                            to.coerceAtMost(Float.MAX_VALUE.toLong()).toFloat()
                        )
                    }
                }

                parameter.isAnnotationPresent(IntRange::class) && type == INT -> {
                    val intRange = parameter.getAnnotationInstance<IntRange>()
                    builder.addIntRange(intRange.min, intRange.max)
                }

                parameter.isAnnotationPresent(LongRange::class) && type == LONG -> {
                    val longRange = parameter.getAnnotationInstance<LongRange>()
                    builder.addLongRange(longRange.min, longRange.max)
                }

                parameter.isAnnotationPresent(DoubleRange::class) && type == DOUBLE -> {
                    val doubleRange = parameter.getAnnotationInstance<DoubleRange>()
                    builder.addDoubleRange(doubleRange.min, doubleRange.max)
                }

                parameter.isAnnotationPresent(FloatRange::class) && type == FLOAT -> {
                    val floatRange = parameter.getAnnotationInstance<FloatRange>()
                    builder.addFloatRange(floatRange.min, floatRange.max)
                }

                else -> {
                    if (isCompact && isInlined) error("Compact and Unnamed cannot be used together")
                    builder.addCodec(ksType, isInlined, isCompact)
                }
            }
        }


        val getter = if (lazy) {
            "getter.value"
        } else {
            "getter"
        }

        return when {
            parameter.hasDefault -> {
                if (!isInlined) {
                    if (isLenient) {
                        builder.add(".lenientOptionalFieldOf(\"%L\")", fieldName)
                    } else {
                        builder.add(".optionalFieldOf(\"%L\")", fieldName)
                    }
                }
                builder.add(
                    ".forGetter { getter -> %T.of(${getter}.%L) },\n",
                    Optional::class.java,
                    name,
                )
                builder.build(this)
                name to Type.DEFAULT
            }

            nullable -> {
                if (!isInlined) {
                    if (isLenient) {
                        builder.add(".lenientOptionalFieldOf(\"%L\")", fieldName)
                    } else {
                        builder.add(".optionalFieldOf(\"%L\")", fieldName)
                    }
                }
                builder.add(
                    ".forGetter { getter -> %T.ofNullable(${getter}.%L) },\n",
                    Optional::class.java,
                    name,
                )
                builder.build(this)
                name to Type.NULLABLE
            }

            else -> {
                if (!isInlined) {
                    builder.add(".fieldOf(\"%L\")", fieldName)
                }
                builder.add(
                    ".forGetter { getter -> ${getter}.%L },\n",
                    name,
                )
                builder.build(this)
                name to Type.NORMAL
            }
        }
    }

    fun extractNames(
        declaration: KSClassDeclaration,
    ): List<Pair<KSValueParameter, Type>> {
        return declaration.primaryConstructor!!.parameters.map { parameter ->
            val nullable = parameter.type.resolve().isMarkedNullable
            parameter to when {
                parameter.hasDefault -> Type.DEFAULT
                nullable -> Type.NULLABLE
                else -> Type.NORMAL
            }
        }
    }

    internal operator fun Range.component1(): Long = this.from
    internal operator fun Range.component2(): Long = this.to

    fun generateCodec(annotation: GenerateCodecData, declaration: KSAnnotated, lazy: Boolean): PropertySpec =
        runCatching {
            if (declaration !is KSClassDeclaration) {
                throw IllegalArgumentException("Declaration is not a class")
            }
            val string = declaration.getField<NamedCodec, String>("name")

            val codecName = (if (lazy) "Lazy" else "") + (string ?: declaration.simpleName.asString()) + "Codec"
            val type = MAP_CODEC_TYPE.parameterizedBy(
                if (lazy) LAZY.parameterizedBy(declaration.toClassName()) else declaration.toClassName()
            )

            if (string != null) {
                builtinCodec.addGeneratedNamedCodec(string, codecName)
            }

            return@runCatching PropertySpec.builder(codecName, type)
                .addModifiers(KModifier.PUBLIC)
                .initializer(
                    CodeBlock.builder().apply {
                        add("CodecUtils.lazyMapCodec {\n")
                        indent()
                        add("%T.mapCodec {\n", RECORD_CODEC_BUILDER_TYPE)
                        indent()
                        add("it.group(\n")

                        indent()
                        for (parameter in declaration.primaryConstructor!!.parameters) {
                            try {
                                createEntry(parameter, declaration, lazy)
                            } catch (t: Throwable) {
                                logger.error("Failed to create codec for ${declaration.location}")
                                throw t
                            }
                        }

                        val args = extractNames(declaration)
                        unindent()

                        if (annotation.createCodecMethod) {
                            add(").apply(it, ::create$codecName)")

                        } else {

                            add(").apply(it) { ${args.joinToString(", ") { "p_${it.first}" }} -> \n")

                            indent()
                            if (lazy) {
                                add("lazy {")
                            }
                            RecordCodecInstanceGenerator.generateCodecInstance(
                                this,
                                args.map { (p, type) -> p.name!!.asString() to type },
                                declaration
                            )
                            if (lazy) {
                                add("}")
                            }
                            unindent()

                            add("}\n")
                        }
                        unindent()
                        add("}\n")
                        unindent()
                        add("}\n")
                    }.build(),
                )
                .build()
        }.onFailure {
            logger.error("Failed to generate ${"lazy".takeIf { lazy } ?: ""}record codec for ${declaration.location}")
        }.getOrThrow()

    enum class Type {
        DEFAULT,
        NULLABLE,
        NORMAL
    }

    private fun List<KSTypeArgument>.getRef(index: Int): KSTypeReference = this[index].type!!
}
