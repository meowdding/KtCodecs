package me.owdding.ktcodecs.generators

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import me.owdding.ktcodecs.BuiltinCodecs
import me.owdding.ktcodecs.FieldName
import me.owdding.ktcodecs.NamedCodec
import me.owdding.ktcodecs.utils.*
import me.owdding.ktcodecs.utils.AnnotationUtils.getField
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

    private fun CodeLineBuilder.addCodec(type: KSType) {
        when (type.starProjection().toTypeName()) {
            LAZY.parameterizedBy(STAR) -> {
                add("getLazyCodec<%T>()", type.arguments[0].type!!.resolve().toTypeName())
            }

            List::class.asClassName().parameterizedBy(STAR) -> {
                addCodec(type.arguments[0].type!!.resolve())
                add(".listOf()")
            }

            MUTABLE_LIST.parameterizedBy(STAR) -> {
                add("CodecUtils.list(")
                addCodec(type.arguments[0].type!!.resolve())
                add(")")
            }

            MUTABLE_SET.parameterizedBy(STAR) -> {
                add("CodecUtils.mutableSet(")
                addCodec(type.arguments[0].type!!.resolve())
                add(")")
            }


            Set::class.asClassName().parameterizedBy(STAR) -> {
                add("CodecUtils.set(")
                addCodec(type.arguments[0].type!!.resolve())
                add(")")
            }

            Map::class.asClassName().parameterizedBy(STAR) -> {
                add("%T.unboundedMap(", CODEC_TYPE)
                addCodec(type.arguments[0].type!!.resolve())
                add(", ")
                addCodec(type.arguments[1].type!!.resolve())
                add(")")
            }

            MUTABLE_MAP.parameterizedBy(STAR) -> {
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

    private fun CodeBlock.Builder.createEntry(
        parameter: KSValueParameter,
        declaration: KSClassDeclaration,
        lazy: Boolean,
    ): Pair<String, Type> {
        val fieldName = parameter.getField<FieldName, String>("value") ?: parameter.name!!.asString()
        val name = parameter.name!!.asString()

        val nullable = parameter.type.resolve().isMarkedNullable
        val ksType = parameter.type.resolve()

        val builder = CodeLineBuilder()

        val namedCodec = parameter.getField<NamedCodec, String>("name")
        if (namedCodec != null) {
            try {
                builder.add(builtinCodec.namedCodecs[namedCodec]!!)
            } catch (e: NullPointerException) {
                throw RuntimeException("Required unknown named codec $namedCodec", e)
            }
        } else {
            builder.addCodec(ksType)
        }


        val getter = if (lazy) {
            "getter.value"
        } else {
            "getter"
        }

        return when {
            parameter.hasDefault -> {
                builder.add(
                    ".optionalFieldOf(\"%L\").forGetter { getter -> %T.of(${getter}.%L) },\n",
                    fieldName,
                    Optional::class.java,
                    name,
                )
                builder.build(this)
                name to Type.DEFAULT
            }

            nullable -> {
                builder.add(
                    ".optionalFieldOf(\"%L\").forGetter { getter -> %T.ofNullable(${getter}.%L) },\n",
                    fieldName,
                    Optional::class.java,
                    name,
                )
                builder.build(this)
                name to Type.NULLABLE
            }

            else -> {
                builder.add(
                    ".fieldOf(\"%L\").forGetter { getter -> ${getter}.%L },\n",
                    fieldName,
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
