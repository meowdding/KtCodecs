@file:Suppress("KotlinPrintToLogpoint")

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

internal inline fun <reified T> encodeParsePrint(name: String, data: T) {
    println("-".repeat(15))
    println(name)

    println("original: $data")
    val encoded = encode(data)
    println("encoded: $encoded")
    val parsed = parse<T>(encoded)
    println("parsed: $parsed")
    require(data == parsed) { "Parsed data is not equal to the original data!" }
    println()
}

internal inline fun <reified T : Any> encodeParsePrint(factory: () -> T) {
    val data = factory()
    val name = data::class.simpleName!!
    encodeParsePrint(name, data)
}

/**
 * You can change things here at your heart's content, this is supposed to just be for testing
 */
fun main() {

    encodeParsePrint {
        DeepReference(
            Thingy(
                listOf(
                    mapOf(
                        "mrrp?" to 5,
                        "haiii" to 67,
                    ),
                    mapOf(
                        "54138745317" to 5,
                        ":3" to -1,
                    )
                ),
                "mewo"
            )
        )
    }

    encodeParsePrint {
        MultipleGenerics<ComponentEnum, String>(
            ComponentEnum.MEOW,
            5,
            "maow",
            listOf(ComponentEnum.MRRRRP),
            mutableListOf("mewo"),
            mutableMapOf("maowww" to mutableSetOf(ComponentEnum.MEOW, ComponentEnum.MRRRRP))
        )
    }

    encodeParsePrint {
        EnumMap<CostTypes, Int>(CostTypes::class.java).apply {
            put(CostTypes.COINS, 45)
        }
    }
}
