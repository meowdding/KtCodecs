package me.owdding.ktmodules

import me.owdding.ktcodecs.*
import java.util.EnumMap
import java.util.EnumSet

@GenerateCodec
data class OptionalTest(
    @OptionalString("fdas") val string: String = "fdas",
    @OptionalInt(1312) val int: Int = 1312,
    @OptionalLong(-514L) val long: Long = -514L,
    @OptionalFloat(54154.59f) val float: Float = 54154.59f,
    @OptionalDouble(5145.514) val double: Double = 5145.514,
    @OptionalBoolean(false) val bool: Boolean = false,
    @OptionalNullable val nullableValue: String? = null,

    // test for a bunch of different types of collections and maps
    @OptionalIfEmpty val list: List<Int> = listOf(),
    @OptionalIfEmpty val mutableList: MutableList<Int> = mutableListOf(),
    @OptionalIfEmpty val mutableSet: MutableSet<Int> = mutableSetOf(),
    @OptionalIfEmpty var map: Map<String, String> = mapOf(),
    @OptionalIfEmpty val mutableMap: MutableMap<String, Int> = mutableMapOf(),
    @OptionalIfEmpty val enumSet: EnumSet<TestEnum> = EnumSet.noneOf(TestEnum::class.java),
    @OptionalIfEmpty val enumMap: EnumMap<TestEnum, String> = EnumMap(TestEnum::class.java),
)