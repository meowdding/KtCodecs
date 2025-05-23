package me.owdding.ktcodecs.generators

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import me.owdding.ktcodecs.*
import me.owdding.ktcodecs.IntRange
import me.owdding.ktcodecs.LongRange
import me.owdding.ktcodecs.utils.*
import me.owdding.ktcodecs.utils.AnnotationUtils.getAnnotation
import me.owdding.ktcodecs.utils.AnnotationUtils.getAnnotationInstance
import me.owdding.ktcodecs.utils.AnnotationUtils.getField
import me.owdding.ktcodecs.utils.AnnotationUtils.resolveClassName
import java.util.*

internal object RecordCodecGenerator {

    lateinit var logger: KSPLogger
    lateinit var builtinCodec: BuiltinCodecs
    private const val MAX_PARAMETERS = 16

    private fun isValid(parameter: KSValueParameter, logger: KSPLogger, builtinCodecs: BuiltinCodecs): Boolean {
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
                    return false
                }
            }
            return true
        }
        return false
    }

    fun isValid(declaration: KSAnnotated?, logger: KSPLogger, builtinCodecs: BuiltinCodecs): Boolean {
        if (declaration !is KSClassDeclaration) {
            logger.error("Declaration is not a class")
        } else if (declaration.modifiers.contains(Modifier.INLINE)) {
            logger.error("@GenerateCodec can only be applied to non-inline classes")
        } else if (!declaration.isPublic()) {
            logger.error("@GenerateCodec can only be applied to public classes")
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
        } else if (!declaration.primaryConstructor!!.parameters.all { isValid(it, logger, builtinCodecs) }) {
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
        val isUnnamed = parameter.getAnnotation<Unnamed>() != null

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
            when {
                parameter.isAnnotationPresent(IntRange::class) -> {
                    val intRange = parameter.getAnnotationInstance<IntRange>()
                    builder.addIntRange(intRange.min, intRange.max)
                }
                parameter.isAnnotationPresent(LongRange::class) -> {
                    val longRange = parameter.getAnnotationInstance<LongRange>()
                    builder.addLongRange(longRange.min, longRange.max)
                }
                parameter.isAnnotationPresent(DoubleRange::class) -> {
                    val doubleRange = parameter.getAnnotationInstance<DoubleRange>()
                    builder.addDoubleRange(doubleRange.min, doubleRange.max)
                }
                parameter.isAnnotationPresent(FloatRange::class) -> {
                    val floatRange = parameter.getAnnotationInstance<FloatRange>()
                    builder.addFloatRange(floatRange.min, floatRange.max)
                }
                else -> {
                    if (isCompact && isUnnamed) error("Compact and Unnamed cannot be used together")
                    builder.addCodec(ksType, isUnnamed, isCompact)
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
                if (!isUnnamed) {
                    builder.add(".optionalFieldOf(\"%L\")", fieldName)
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
                if (!isUnnamed) {
                    builder.add(".optionalFieldOf(\"%L\")", fieldName)
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
                if (!isUnnamed) {
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

    fun generateCodec(declaration: KSAnnotated, lazy: Boolean): PropertySpec {
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

        return PropertySpec.builder(codecName, type)
            .addModifiers(if (string != null) KModifier.PRIVATE else KModifier.PUBLIC)
            .initializer(
                CodeBlock.builder().apply {
                    add("CodecUtils.lazyMapCodec {\n")
                    indent()
                    add("%T.mapCodec {\n", RECORD_CODEC_BUILDER_TYPE)
                    indent()
                    add("it.group(\n")
                    val args = mutableListOf<Pair<String, Type>>()

                    indent()
                    for (parameter in declaration.primaryConstructor!!.parameters) {
                        createEntry(parameter, declaration, lazy).let { args.add(it) }
                    }
                    unindent()

                    add(").apply(it) { ${args.joinToString(", ") { "p_${it.first}" }} -> \n")

                    indent()
                    if (lazy) {
                        add("lazy {")
                    }
                    RecordCodecInstanceGenerator.generateCodecInstance(this, args, declaration)
                    if (lazy) {
                        add("}")
                    }
                    unindent()

                    add("}\n")
                    unindent()
                    add("}\n")
                    unindent()
                    add("}\n")
                }.build(),
            )
            .build()
    }

    enum class Type {
        DEFAULT,
        NULLABLE,
        NORMAL
    }

    private fun List<KSTypeArgument>.getRef(index: Int): KSTypeReference = this[index].type!!
}
