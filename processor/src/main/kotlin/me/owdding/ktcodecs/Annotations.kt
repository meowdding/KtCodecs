package me.owdding.ktcodecs

import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateCodec(val generateDefault: Boolean = true, val generateLazy: Boolean = false)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateDispatchCodec(val value: KClass<*>)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class IncludedCodec(

    /**
     * Keyable means it can be used in a key of a map meaning its serialized to a string
     */
    val keyable: Boolean = false,

    val named: String = ":3"
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
annotation class NamedCodec(val name: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Unnamed

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FieldName(val value: String)
