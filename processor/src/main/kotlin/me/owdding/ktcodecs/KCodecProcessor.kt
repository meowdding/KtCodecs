package me.owdding.ktcodecs

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import me.owdding.kotlinpoet.*
import me.owdding.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import me.owdding.kotlinpoet.ksp.toClassName
import me.owdding.kotlinpoet.ksp.writeTo
import me.owdding.ktcodecs.generators.CreatorMethodGenerator
import me.owdding.ktcodecs.generators.DispatchCodecGenerator
import me.owdding.ktcodecs.generators.RecordCodecGenerator
import me.owdding.ktcodecs.utils.AnnotationUtils.getField
import me.owdding.ktcodecs.utils.CODEC_TYPE
import me.owdding.ktcodecs.utils.GenerateCodecData
import me.owdding.ktcodecs.utils.JAVA_CLASS
import me.owdding.ktcodecs.utils.KTYPE
import me.owdding.ktcodecs.utils.LAZY
import me.owdding.ktcodecs.utils.MAP_CODEC_TYPE
import java.io.OutputStreamWriter

internal class KCodecProcessor(
    private val generator: CodeGenerator,
    private val logger: KSPLogger,
    private val context: ModuleContext,
) : SymbolProcessor {

    private var ran = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (ran) return emptyList()
        ran = true
        RecordCodecGenerator.logger = logger

        val builtinCodecs = BuiltinCodecs()
        resolver.getSymbolsWithAnnotation(IncludedCodec::class.qualifiedName!!)
            .forEach { builtinCodecs.add(it, logger) }

        val annotated = resolver.getSymbolsWithAnnotation(GenerateCodec::class.qualifiedName!!).toList()
        val validGeneratedCodecs = annotated.filter { RecordCodecGenerator.isValid(it, logger, builtinCodecs) }
        RecordCodecGenerator.builtinCodec = builtinCodecs

        val instances = validGeneratedCodecs.associateWith() {
            val lazy = it.getField<GenerateCodec, Boolean>("generateLazy")!!
            val default = it.getField<GenerateCodec, Boolean>("generateDefault")!!
            val method = it.getField<GenerateCodec, Boolean>("createCodecMethod")!!
            val generic = it is KSClassDeclaration && it.typeParameters.isNotEmpty()

            GenerateCodecData(
                lazy,
                default,
                method,
                generic,
            )
        }

        val generatedLazyCodecs = instances.filter { (_, key) -> key.generateLazy && !key.isGeneric }
            .map { (value, key) -> RecordCodecGenerator.generateCodec(key, value, true) }
        val generatedCodecs = instances.filter { (_, key) -> key.generateDefault && !key.isGeneric }
            .map { (value, key) -> RecordCodecGenerator.generateCodec(key, value, false) }

        val generatedLazyGenericCodecs = instances.filter { (_, key) -> key.generateLazy && key.isGeneric }
            .map { (value, key) -> RecordCodecGenerator.generateGenericCodec(key, value, true) }
        val generatedGenericCodecs = instances.filter { (_, key) -> key.generateDefault && key.isGeneric }
            .map { (value, key) -> RecordCodecGenerator.generateGenericCodec(key, value, false) }

        val lazyCodecCreators = instances.filter { (_, key) -> key.generateLazy && key.createCodecMethod }
            .map { (value, key) -> CreatorMethodGenerator.createMethod(key, value, true) }
        val codecCreators = instances.filter { (_, key) -> key.createCodecMethod && key.generateDefault }
            .map { (value, key) -> CreatorMethodGenerator.createMethod(key, value, false) }

        val dependencies = Dependencies(true, *annotated.mapNotNull { it.containingFile }.toTypedArray())
        createBuiltin(generator, dependencies, "EnumCodec", BuiltinCodecClasses.ENUM_CODEC)
        createBuiltin(generator, dependencies, "CodecUtils", BuiltinCodecClasses.CODEC_UTILS)
        createBuiltin(generator, dependencies, "DispatchHelper", BuiltinCodecClasses.DISPATCH_HELPER)
        createBuiltin(generator, dependencies, "AliasMapCodec", BuiltinCodecClasses.ALIAS_CODEC)
        createBuiltin(generator, dependencies, "OptionalAliasMapCodec", BuiltinCodecClasses.OPTIONAL_ALIAS_CODEC)

        val annotatedDispatch = resolver.getSymbolsWithAnnotation(GenerateDispatchCodec::class.qualifiedName!!).toList()
        val dispatchCodecs = DispatchCodecGenerator.create(annotatedDispatch, logger, builtinCodecs)

        /*
        @file:Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN", "FunctionName", "LocalVariableName",
        "RemoveRedundantQualifierName", "RemoveExplicitTypeArguments", "RedundantVisibilityModifier", "unused",
        "RedundantCompanionReference"
        )
         */
        val warnings = listOf("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN", "FunctionName", "LocalVariableName",
            "RemoveRedundantQualifierName", "RemoveExplicitTypeArguments", "RedundantVisibilityModifier", "unused",
            "RedundantCompanionReference")

        val file = FileSpec.builder(context.generatedPackage, "${context.projectName}Codecs")
            .indent("    ")
            .addImport("kotlin.reflect", "typeOf")
            .addAnnotation(
                AnnotationSpec.builder(Suppress::class).apply {
                    this.addMember(warnings.joinToString { "\"$it\"" })
                }.build(),
            )
            .addType(
                TypeSpec.objectBuilder("${context.projectName}Codecs").apply {
                    this.addModifiers(KModifier.INTERNAL)

                    this.addProperties(generatedCodecs)
                    this.addProperties(generatedLazyCodecs)
                    this.addProperties(dispatchCodecs)

                    this.addFunctions(generatedLazyGenericCodecs)
                    this.addFunctions(generatedGenericCodecs)

                    this.addFunctions(lazyCodecCreators)
                    this.addFunctions(codecCreators)

                    this.addFunction(
                        FunSpec.builder("getClass").apply {
                            this.addModifiers(KModifier.PRIVATE)
                            this.receiver(KTYPE)
                            this.returns(JAVA_CLASS.parameterizedBy(STAR))
                            this.addCode("return (this.classifier as kotlin.reflect.KClass<*>).java")
                        }.build()
                    )

                    this.addFunction(
                        FunSpec.builder("get").apply {
                            this.addModifiers(KModifier.PRIVATE, KModifier.OPERATOR)
                            this.receiver(KTYPE)
                            this.returns(KTYPE)
                            this.addParameter("index", INT)
                            this.addCode("return arguments[index].type ?: typeOf<Any?>()")
                        }.build()
                    )

                    this.addFunction(
                        FunSpec.builder("getLazyCodec").apply {
                            this.addModifiers(KModifier.INLINE)
                            this.addTypeVariable(TypeVariableName("T").copy(reified = true))
                            this.returns(CODEC_TYPE.parameterizedBy(LAZY.parameterizedBy(TypeVariableName("T"))))
                            this.addCode("return getLazyCodec(typeOf<T>()) as Codec<Lazy<T>>")
                        }.build(),
                    )

                    this.addFunction(
                        FunSpec.builder("getLazyMapCodec").apply {
                            this.addModifiers(KModifier.INLINE)
                            this.addTypeVariable(TypeVariableName("T").copy(reified = true))
                            this.returns(MAP_CODEC_TYPE.parameterizedBy(LAZY.parameterizedBy(TypeVariableName("T"))))
                            this.addCode("return getLazyMapCodec(typeOf<T>()) as MapCodec<Lazy<T>>")
                        }.build(),
                    )

                    this.addFunction(
                        FunSpec.builder("getCodec").apply {
                            this.addModifiers(KModifier.INLINE)
                            this.addTypeVariable(TypeVariableName("T").copy(reified = true))
                            this.returns(CODEC_TYPE.parameterizedBy(TypeVariableName("T")))
                            this.addCode("return getCodec(typeOf<T>()) as Codec<T>")
                        }.build(),
                    )

                    this.addFunction(
                        FunSpec.builder("getMapCodec").apply {
                            this.addModifiers(KModifier.INLINE)
                            this.addTypeVariable(TypeVariableName("T").copy(reified = true))
                            this.returns(MAP_CODEC_TYPE.parameterizedBy(TypeVariableName("T")))
                            this.addCode("return getMapCodec(typeOf<T>()) as MapCodec<T>")
                        }.build(),
                    )

                    this.addFunction(
                        FunSpec.builder("getLazyMapCodec").apply {
                            this.addParameter("type", KTYPE)
                            this.returns(
                                MAP_CODEC_TYPE.parameterizedBy(
                                    WildcardTypeName.producerOf(LAZY.parameterizedBy(STAR))
                                )
                            )
                            this.addCode("return when (type.getClass()) {\n")
                            for (codec in validGeneratedCodecs.filter { it.getField<GenerateCodec, Boolean>("generateLazy")!! }) {
                                val string = codec.getField<NamedCodec, String>("name")

                                val codecName =
                                    (string ?: (codec as KSClassDeclaration).simpleName.asString()) + "Codec"
                                this.addCode(
                                    "    %T::class.java -> Lazy%L\n",
                                    (codec as KSClassDeclaration).toClassName(),
                                    codecName + addArgumentTypesIfNeeded(codec),
                                )
                            }
                            this.addCode("    else -> CodecUtils.toLazy(getMapCodec(type))\n")
                            this.addCode("}\n")
                        }.build()
                    )

                    this.addFunction(
                        FunSpec.builder("getLazyCodec").apply {
                            this.addParameter("type", KTYPE)
                            this.returns(
                                CODEC_TYPE.parameterizedBy(
                                    WildcardTypeName.producerOf(LAZY.parameterizedBy(STAR))
                                )
                            )
                            this.addCode("return when {\n")
                            //builtinCodecs.filterNot { (_, info) -> info.mapCodec }.forEach { type, info ->
                            //    this.addCode("    clazz == %T::class.java -> ${info.codec}\n", type)
                            //}
                            //this.addCode("    clazz.isEnum -> EnumCodec.forKCodec(clazz.enumConstants)\n")
                            this.addCode("    else -> getLazyMapCodec(type).codec()\n")
                            this.addCode("}\n")
                        }.build(),
                    )


                    this.addFunction(
                        FunSpec.builder("getLazyMapCodec").apply {
                            this.addParameter("clazz", JAVA_CLASS.parameterizedBy(STAR))
                            this.returns(
                                MAP_CODEC_TYPE.parameterizedBy(
                                    WildcardTypeName.producerOf(LAZY.parameterizedBy(STAR))
                                )
                            )
                            this.addCode("return getLazyMapCodec(kotlin.jvm.internal.Reflection.typeOf(clazz))")
                            this.addKdoc("Doesn't support generics")
                        }.build()
                    )


                    this.addFunction(
                        FunSpec.builder("getLazyCodec").apply {
                            this.addParameter("clazz", JAVA_CLASS.parameterizedBy(STAR))
                            this.returns(
                                CODEC_TYPE.parameterizedBy(
                                    WildcardTypeName.producerOf(LAZY.parameterizedBy(STAR))
                                )
                            )
                            this.addCode("return getLazyCodec(kotlin.jvm.internal.Reflection.typeOf(clazz))")
                            this.addKdoc("Doesn't support generics")
                        }.build()
                    )

                    // this is here because DispatchHelper uses java.lang.Class<*>
                    this.addFunction(
                        FunSpec.builder("getMapCodec").apply {
                            this.addParameter("clazz", JAVA_CLASS.parameterizedBy(STAR))
                            this.returns(MAP_CODEC_TYPE.parameterizedBy(STAR))
                            this.addCode("return getMapCodec(kotlin.jvm.internal.Reflection.typeOf(clazz))")
                            this.addKdoc("Doesn't support generics")
                        }.build(),
                    )

                    // here so this can still be used (more or less, it won't have support for generics still)
                    this.addFunction(
                        FunSpec.builder("getCodec").apply {
                            this.addParameter("clazz", JAVA_CLASS.parameterizedBy(STAR))
                            this.returns(CODEC_TYPE.parameterizedBy(STAR))
                            this.addCode("return getCodec(kotlin.jvm.internal.Reflection.typeOf(clazz))")
                            this.addKdoc("Doesn't support generics")
                        }.build(),
                    )

                    this.addFunction(
                        FunSpec.builder("getMapCodec").apply {
                            this.addParameter("type", KTYPE)
                            this.returns(MAP_CODEC_TYPE.parameterizedBy(STAR))
                            this.addCode("return when (type) {\n")
                            val mapCodecs = builtinCodecs.filterValues { it.mapCodec }
                            mapCodecs.filterKeys { type ->
                                type is ParameterizedTypeName
                            }.forEach { (type, info) ->
                                this.addCode("    typeOf<%T>() -> ${info.codec}\n", type)
                            }
                            this.addCode("    else -> when (type.getClass()) {\n")

                            mapCodecs.filterKeys { type ->
                                type !is ParameterizedTypeName
                            }.forEach { (type, info) ->
                                this.addCode("        %T::class.java -> ${info.codec}\n", type)
                            }
                            for (codec in validGeneratedCodecs.filter { it.getField<GenerateCodec, Boolean>("generateDefault")!! }) {
                                val string = codec.getField<NamedCodec, String>("name")

                                val codecName =
                                    (string ?: (codec as KSClassDeclaration).simpleName.asString()) + "Codec"

                                this.addCode(
                                    "        %T::class.java -> %L\n",
                                    (codec as KSClassDeclaration).toClassName(),
                                    codecName + addArgumentTypesIfNeeded(codec),
                                )
                            }
                            this.addCode("        else -> throw IllegalArgumentException(\"Unknown codec for type: \$type\")\n")
                            this.addCode("    }\n")
                            this.addCode("}\n")
                        }.build()
                    )
                    this.addFunction(
                        FunSpec.builder("getCodec").apply {
                            this.addParameter("type", KTYPE)
                            this.returns(CODEC_TYPE.parameterizedBy(STAR))
                            this.addCode("return when (type) {\n")
                            builtinCodecs.filterNot { (type, info) -> info.mapCodec || type !is ParameterizedTypeName }.forEach { (type, info) ->
                                this.addCode("    typeOf<%T>() -> ${info.codec}\n", type)
                            }
                            this.addCode("    else -> when (val clazz = type.getClass()) {\n")
                            builtinCodecs.filterNot { (type, info) -> info.mapCodec || type is ParameterizedTypeName }.forEach { (type, info) ->
                                this.addCode("        %T::class.java -> ${info.codec}\n", type)
                            }
                            this.addCode("        MutableList::class.java -> CodecUtils.mutableList(getCodec(type[0]))\n")
                            this.addCode("        MutableSet::class.java -> CodecUtils.mutableSet(getCodec(type[0]))\n")
                            this.addCode("        MutableMap::class.java -> CodecUtils.map(getCodec(type[0]), getCodec(type[1]))\n")
                            this.addCode("        java.util.EnumSet::class.java -> CodecUtils.enumSet(type[0].getClass() as Class<Nothing>, getCodec(type[0]) as Codec<Nothing>)\n")
                            this.addCode("        java.util.EnumMap::class.java -> CodecUtils.enumMap(type[0].getClass() as Class<Nothing>, getCodec(type[0]) as Codec<Nothing>, getCodec(type[1]))\n")
                            this.addCode("        com.mojang.datafixers.util.Either::class.java -> Codec.either(getCodec(type[0]), getCodec(type[1]))\n")
                            this.addCode("        else -> {\n")
                            this.addCode("            if (clazz.isEnum) EnumCodec.forKCodec(clazz.enumConstants)\n")
                            this.addCode("            else getMapCodec(type).codec()\n")
                            this.addCode("        }\n")
                            this.addCode("    }\n")
                            this.addCode("}\n")
                        }.build(),
                    )

                }.build(),
            )

        file.build().writeTo(generator, dependencies)

        return emptyList()
    }

    private fun createBuiltin(generator: CodeGenerator, dependencies: Dependencies, name: String, body: String) {
        OutputStreamWriter(generator.createNewFile(dependencies, context.generatedPackage, name)).use {
            it.write(
                body.replace(BuiltinCodecClasses.PACKAGE_IDENTIFIER, context.generatedPackage)
                    .replace(BuiltinCodecClasses.CODECS_IDENTIFIER, "${context.projectName}Codecs")
            )
        }
    }

    private fun addArgumentTypesIfNeeded(codec: KSAnnotated): String {
        val typeParameters = (codec as KSClassDeclaration).typeParameters
        return if (typeParameters.isEmpty()) ""
        else buildString {
            append("<")
            repeat(typeParameters.size) {
                if (it > 0) append(", ")
                append("Nothing")
            }
            append(">")
            append("(")
            repeat(typeParameters.size) {
                if (it > 0) append(", ")
                append("type[$it]")
            }
            append(")")
        }
    }
}

internal class KCodecProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment,
    ): SymbolProcessor = KCodecProcessor(
        environment.codeGenerator,
        environment.logger,
        ModuleContext.create(environment.options, environment.logger)
    )
}

internal data class ModuleContext(
    val projectName: String,
    val generatedPackage: String,
) {
    companion object {
        fun create(
            options: Map<String, String>,
            logger: KSPLogger,
        ): ModuleContext {
            return ModuleContext(
                this.require("project_name", options, logger).let {
                    it.replaceFirstChar { first -> first.uppercase() }
                },
                this.require("package", options, logger),
            )
        }

        private fun require(option: String, map: Map<String, String>, logger: KSPLogger): String {
            return requireNotNull(map["meowdding.codecs.$option"] ?: map["meowdding.$option"], logger)
        }

        private fun <T> requireNotNull(value: T?, logger: KSPLogger): T {
            if (value == null) {
                """
                Please make sure to include the following in your build.gradle(.kts) file!
                
                ksp {
                    arg("meowdding.codecs.project_name", project.name)
                    arg("meowdding.codecs.package", TODO("Change to your generated code package"))
                }
                """.trimIndent().split("\n").forEach { logger.error(it) }
                throw IllegalStateException("Module processor wasn't configured correctly!")
            }
            return value
        }
    }
}