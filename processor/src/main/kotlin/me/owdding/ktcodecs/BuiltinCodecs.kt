@file:OptIn(KspExperimental::class)

package me.owdding.ktcodecs

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import me.owdding.kotlinpoet.ClassName
import me.owdding.kotlinpoet.TypeName
import me.owdding.kotlinpoet.ksp.toTypeName
import me.owdding.ktcodecs.utils.AnnotationUtils.resolveClassName
import me.owdding.ktcodecs.utils.CODEC_TYPE
import me.owdding.ktcodecs.utils.MAP_CODEC_TYPE

internal class BuiltinCodecs : MutableMap<TypeName, Info> by mutableMapOf(){

    private val codecs: MutableMap<TypeName, Info> = this
    val namedCodecs: MutableMap<String, String> = mutableMapOf()

    init {
        this.add("java.lang", "String", "com.mojang.serialization.Codec.STRING", true)
        this.add("kotlin", "String", "com.mojang.serialization.Codec.STRING", true)
        this.add("java.lang", "Boolean", "com.mojang.serialization.Codec.BOOL")
        this.add("kotlin", "Boolean", "com.mojang.serialization.Codec.BOOL")
        this.add("java.lang", "Byte", "com.mojang.serialization.Codec.BYTE")
        this.add("kotlin", "Byte", "com.mojang.serialization.Codec.BYTE")
        this.add("java.lang", "Short", "com.mojang.serialization.Codec.SHORT")
        this.add("kotlin", "Short", "com.mojang.serialization.Codec.SHORT")
        this.add("java.lang", "Integer", "com.mojang.serialization.Codec.INT")
        this.add("kotlin", "Int", "com.mojang.serialization.Codec.INT")
        this.add("java.lang", "Long", "com.mojang.serialization.Codec.LONG")
        this.add("kotlin", "Long", "com.mojang.serialization.Codec.LONG")
        this.add("java.lang", "Float", "com.mojang.serialization.Codec.FLOAT")
        this.add("kotlin", "Float", "com.mojang.serialization.Codec.FLOAT")
        this.add("java.lang", "Double", "com.mojang.serialization.Codec.DOUBLE")
        this.add("kotlin", "Double", "com.mojang.serialization.Codec.DOUBLE")
        this.add("java.util", "UUID", "CodecUtils.UUID_CODEC", true)
    }

    fun add(packageName: String, className: String, codec: String, keyable: Boolean = false) = add(ClassName(packageName, className), codec, keyable)

    fun add(type: TypeName, value: String, keyable: Boolean = false, mapCodec: Boolean = false): Boolean {
        if (type !in codecs) {
            codecs[type] = Info(value, keyable, mapCodec)
            return true
        }
        return false
    }

    fun addGeneratedNamedCodec(name: String, property: String) {
        this.namedCodecs.put(name, property)
    }

    fun add(declaration: KSAnnotated, logger: KSPLogger) {
        if (isValid(declaration, logger)) {
            declaration as KSPropertyDeclaration
            val type = declaration.type.resolve().arguments[0].toTypeName()
            val isKeyable = declaration.getAnnotationsByType(IncludedCodec::class).first().keyable
            val isNamed = declaration.getAnnotationsByType(IncludedCodec::class).first().named.takeUnless { it == ":3" }

            if (isNamed != null) {
                this.namedCodecs.put(isNamed, declaration.qualifiedName!!.asString())?.let {
                    throw UnsupportedOperationException("Found duplicate named codec $isNamed")
                }
                logger.warn("Found named codec $isNamed")
                return
            }

            val isMapCodec = declaration.type.resolveClassName() == MAP_CODEC_TYPE

            if (!add(type, declaration.qualifiedName!!.asString(), isKeyable, isMapCodec)) {
                logger.error("Duplicate included codec for $type")
            }
        }
    }

    fun isStringType(type: TypeName): Boolean {
        return codecs[type]?.keyable ?: false
    }

    companion object {
        fun isValid(declaration: KSAnnotated?, logger: KSPLogger): Boolean {
            if (declaration !is KSPropertyDeclaration) {
                logger.error("Declaration is not a property")
            } else if (!declaration.isPublic() && !declaration.isInternal()) {
                logger.error("@IncludedCodec can only be applied to public or internal properties")
            } else if (declaration.isLocal() || declaration.parentDeclaration == null || declaration.parentDeclaration !is KSClassDeclaration || (declaration.parentDeclaration as KSClassDeclaration).classKind != ClassKind.OBJECT) {
                logger.error("@IncludedCodec can only be applied to public properties in objects")
            } else if (!declaration.parentDeclaration!!.isPublic() && !declaration.parentDeclaration!!.isInternal()) {
                logger.error("@IncludedCodec can only be applied to public properties in public objects")
            } else if (declaration.type.resolveClassName() != CODEC_TYPE && declaration.type.resolveClassName() != MAP_CODEC_TYPE) {
                logger.error("@IncludedCodec can only be applied to properties that are Codec<T> or MapCodec<T> was ${declaration.type.resolve().toTypeName()} at ${declaration.simpleName.asString()}")
            } else {
                return true
            }
            return false
        }
    }
}

data class Info(
    val codec: String,
    val keyable: Boolean,
    val mapCodec: Boolean,
)

