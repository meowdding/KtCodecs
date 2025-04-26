package me.owdding.ktcodecs

import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
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

        val builtinCodecs = BuiltinCodecs()
        resolver.getSymbolsWithAnnotation(IncludedCodec::class.qualifiedName!!).forEach { builtinCodecs.add(it, logger) }

        val annotated = resolver.getSymbolsWithAnnotation(GenerateCodec::class.qualifiedName!!).toList()
        val validGeneratedCodecs = annotated.filter { RecordCodecGenerator.isValid(it, logger, builtinCodecs) }
        val generatedCodecs = validGeneratedCodecs.map { RecordCodecGenerator.generateCodec(it) }

        val file = FileSpec.builder(context.generatedPackage, "${context.projectName}Codecs")
            .indent("    ")
            .addType(
                TypeSpec.objectBuilder("${context.projectName}Codecs").apply {
                    this.addModifiers(KModifier.INTERNAL)

                    this.addProperties(generatedCodecs)

                    this.addFunction(
                        FunSpec.builder("getCodec").apply {
                            this.addModifiers(KModifier.INLINE)
                            this.addTypeVariable(TypeVariableName("T").copy(reified = true))
                            this.returns(
                                ClassName("com.mojang.serialization", "Codec")
                                    .parameterizedBy(TypeVariableName("T")),
                            )
                            this.addCode("return getCodec(T::class.java) as Codec<T>")
                        }.build(),
                    )

                    this.addFunction(
                        FunSpec.builder("getCodec").apply {
                            this.addParameter("clazz", ClassName("java.lang", "Class").parameterizedBy(STAR))
                            this.returns(ClassName("com.mojang.serialization", "Codec").parameterizedBy(STAR))
                            this.addCode("return when {\n")
                            builtinCodecs.forEach { type, info ->
                                this.addCode("    clazz == %T::class.java -> ${info.codec}\n", type)
                            }
                            this.addCode("    clazz.isEnum -> EnumCodec.forKCodec(clazz.enumConstants)\n")
                            for (codec in validGeneratedCodecs) {
                                this.addCode(
                                    "    clazz == %T::class.java -> %L\n",
                                    (codec as KSClassDeclaration).toClassName(),
                                    "${codec.simpleName.asString()}Codec",
                                )
                            }
                            this.addCode("    else -> throw IllegalArgumentException(\"Unknown codec for class: \$clazz\")\n")
                            this.addCode("}\n")
                        }.build(),
                    )

                }.build(),
            )

        val dependencies = Dependencies(true, *annotated.mapNotNull { it.containingFile }.toTypedArray())

        file.build().writeTo(generator, dependencies)

        OutputStreamWriter(generator.createNewFile(dependencies, context.generatedPackage, "EnumCodec")).use {
            it.write(BuiltinCodecClasses.ENUM_CODEC.replace(BuiltinCodecClasses.PACKAGE_IDENTIFIER, context.generatedPackage))
        }

        OutputStreamWriter(generator.createNewFile(dependencies, context.generatedPackage, "CodecUtils")).use {
            it.write(BuiltinCodecClasses.CODEC_UTILS.replace(BuiltinCodecClasses.PACKAGE_IDENTIFIER, context.generatedPackage))
        }

        return emptyList()
    }

}

internal class KCodecProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor = KCodecProcessor(environment.codeGenerator, environment.logger, ModuleContext.create(environment.options, environment.logger))
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
                this.require("meowdding.codecs.project_name", options, logger).let {
                    it.replaceFirstChar { first -> first.uppercase() }
                },
                this.require("meowdding.codecs.package", options, logger),
            )
        }

        private fun require(option: String, map: Map<String, String>, logger: KSPLogger): String {
            return requireNotNull(map[option], logger)
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