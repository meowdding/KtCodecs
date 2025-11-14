package me.owdding.ktcodecs

import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateCodec(
    val generateDefault: Boolean = true,
    val generateLazy: Boolean = false,
    val createCodecMethod: Boolean = false,
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.CLASS)
annotation class GenerateDispatchCodec(
    val value: KClass<*>,
    val typeKey: String = "type",
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY)
annotation class IncludedCodec(

    /**
     * Keyable means it can be used in a key of a map meaning its serialized to a string
     */
    val keyable: Boolean = false,

    val named: String = ":3",
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.CLASS)
annotation class NamedCodec(val name: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Inline

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Lenient

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FieldName(val value: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FieldNames(vararg val value: String)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class IntRange(
    val min: Int = Int.MIN_VALUE,
    val max: Int = Int.MAX_VALUE,
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class LongRange(
    val min: Long = Long.MIN_VALUE,
    val max: Long = Long.MAX_VALUE,
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class DoubleRange(
    val min: Double = Double.MIN_VALUE,
    val max: Double = Double.MAX_VALUE,
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class FloatRange(
    val min: Float = Float.MIN_VALUE,
    val max: Float = Float.MAX_VALUE,
)

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Compact()

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class CustomGetterMethod
