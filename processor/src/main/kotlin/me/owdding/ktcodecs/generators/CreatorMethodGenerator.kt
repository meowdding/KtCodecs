package me.owdding.ktcodecs.generators

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import me.owdding.kotlinpoet.CodeBlock
import me.owdding.kotlinpoet.FunSpec
import me.owdding.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import me.owdding.kotlinpoet.asClassName
import me.owdding.kotlinpoet.ksp.toClassName
import me.owdding.kotlinpoet.ksp.toTypeName
import me.owdding.ktcodecs.NamedCodec
import me.owdding.ktcodecs.utils.AnnotationUtils.getField
import me.owdding.ktcodecs.utils.GenerateCodecData
import me.owdding.ktcodecs.utils.LAZY
import java.util.*

internal object CreatorMethodGenerator {

    fun createMethod(annotation: GenerateCodecData, declaration: KSAnnotated, lazy: Boolean): FunSpec = runCatching {
        if (declaration !is KSClassDeclaration) {
            throw IllegalArgumentException("Declaration is not a class")
        }
        val string = declaration.getField<NamedCodec, String>("name")
        val codecName = (if (lazy) "Lazy" else "") + (string ?: declaration.simpleName.asString()) + "Codec"

        val args = RecordCodecGenerator.extractNames(declaration)

        return@runCatching FunSpec.builder("create$codecName").apply {
            args.forEach { (parameter, type) ->
                val typeName = parameter.type.resolve().toTypeName()
                addParameter("p_" + parameter.name!!.asString(), if (type == RecordCodecGenerator.Type.NORMAL) typeName else Optional::class.asClassName().parameterizedBy(typeName))
            }

            if (lazy) {
                returns(LAZY.parameterizedBy(declaration.toClassName()))
            } else {
                returns(declaration.toClassName())
            }
            if (annotation.generateDefault && lazy) {
                addCode(
                    "return lazy { create${codecName.removePrefix("Lazy")}(${
                        args.joinToString(",") { (key) -> "p_" + key.name!!.asString() }
                    }) }")
                return@apply
            }
            if (lazy) {
                addCode("return lazy {")
            } else {
                addCode("return ")
            }

            addCode(CodeBlock.builder().apply {
                RecordCodecInstanceGenerator.generateCodecInstance(
                    this,
                    args.map { (k, type) -> k.name!!.asString() to type },
                    declaration
                )
            }.build())

            if (lazy) {
                addCode("}")
            }
        }.build()
    }.onFailure {
        RecordCodecGenerator.logger.error("Failed create method for ${declaration.location}")
    }.getOrThrow()

}