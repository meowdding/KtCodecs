package me.owdding.ktcodecs.generators

import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier
import me.owdding.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import me.owdding.kotlinpoet.PropertySpec
import me.owdding.kotlinpoet.ksp.toClassName
import me.owdding.kotlinpoet.ksp.toTypeName
import me.owdding.ktcodecs.BuiltinCodecs
import me.owdding.ktcodecs.GenerateDispatchCodec
import me.owdding.ktcodecs.utils.AnnotationUtils.getField
import me.owdding.ktcodecs.utils.MAP_CODEC_TYPE

internal object DispatchCodecGenerator {

    fun create(annotated: List<KSAnnotated>, logger: KSPLogger, builtinCodecs: BuiltinCodecs): List<PropertySpec> {
        val validGeneratedCodecs = annotated.filter { isValid(it, logger) }
        val generatedCodecs = validGeneratedCodecs.map { generateCodec(it, logger, builtinCodecs) }
        return generatedCodecs
    }

    private fun generateCodec(declaration: KSAnnotated, logger: KSPLogger, builtinCodecs: BuiltinCodecs): PropertySpec = runCatching{
         if (declaration !is KSClassDeclaration) {
            throw IllegalArgumentException("Declaration is not a class")
        }
        val type = declaration.getField<GenerateDispatchCodec, KSType>("value")!!

        builtinCodecs.add(type.toTypeName(), type.toClassName().simpleName + "Codec", mapCodec = true)

        return@runCatching PropertySpec.builder(type.toClassName().simpleName + "Codec",
            MAP_CODEC_TYPE.parameterizedBy(type.toClassName()))
            .initializer("Codec.STRING.dispatchMap({it.type.id}, {%T.getType(it).codec})", declaration.toClassName()).build()
    }.onFailure {
        logger.error("Failed dispatch codec for ${declaration.location}")
    }.getOrThrow()

    private fun isValid(declaration: KSAnnotated, logger: KSPLogger): Boolean {
        if (declaration !is KSClassDeclaration) {
            logger.error("Declaration is not a class")
        } else if (declaration.modifiers.contains(Modifier.INLINE)) {
            logger.error("@GenerateDispatchCodec can only be applied to non-inline classes")
        } else if (!declaration.isPublic() && !declaration.isInternal()) {
            logger.error("@GenerateDispatchCodec can only be applied to public or internal classes")
        } else if (Modifier.ENUM !in declaration.modifiers) {
            logger.error("@GenerateDispatchCodec can only be applied to enum classes")
        }  else {
            return true
        }
        return false
    }

}