package me.owdding.ktcodecs

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import me.owdding.ktcodecs.generated.CodecUtils
import me.owdding.ktcodecs.generated.TestProjectCodecs
import java.util.EnumMap

internal inline fun <reified T> encode(data: T): JsonElement {
    return TestProjectCodecs.getCodec<T>().encodeStart(JsonOps.INSTANCE, data).orThrow
}

internal inline fun <reified T> parse(json: JsonElement): T {
    return TestProjectCodecs.getCodec<T>().parse(JsonOps.INSTANCE, json).orThrow
}

/**
 * You can change things here at your heart's content, this is supposed to just be for testing
 */
fun main() {

    val multipleGenericsData = MultipleGenerics<ComponentEnum, String>(
        ComponentEnum.MEOW,
        5,
        "maow",
        listOf(ComponentEnum.MRRRRP),
        mutableListOf("mewo"),
        mutableMapOf("maowww" to mutableSetOf(ComponentEnum.MEOW, ComponentEnum.MRRRRP))
    )

    val encoded = encode(multipleGenericsData)
    println("encoded: $encoded")
    val parsed = parse<MultipleGenerics<ComponentEnum, String>>(encoded)
    println("parsed: $parsed")

    val codec = CodecUtils.enumMap<CostTypes, Int>()

    val enumMap = EnumMap<CostTypes, Int>(CostTypes::class.java).apply {
        put(CostTypes.COINS, 45)
    }
    val encodedEnumMap = encode(enumMap)
    println("encode: $encodedEnumMap")
    val parsedEnumMap = parse<EnumMap<CostTypes, Int>>(encodedEnumMap)
    println("parsed: $parsedEnumMap")
}
