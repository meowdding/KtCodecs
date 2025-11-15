package me.owdding.ktmodules

import me.owdding.ktcodecs.*

@GenerateCodec
data class OptionalTest(
    @OptionalString("fdas") val string: String = "fdas",
    @OptionalInt(1312) val int: Int = 1312,
    @OptionalLong(-514L) val long: Long = -514L,
    @OptionalFloat(54154.59f) val float: Float = 54154.59f,
    @OptionalDouble(5145.514) val double: Double = 5145.514,
    @OptionalBoolean(false) val bool: Boolean = false,
)