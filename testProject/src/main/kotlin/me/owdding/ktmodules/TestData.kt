package me.owdding.ktmodules

import com.mojang.serialization.Codec
import me.owdding.ktcodecs.FieldName
import me.owdding.ktcodecs.GenerateCodec
import me.owdding.ktcodecs.IncludedCodec
import me.owdding.ktcodecs.NamedCodec

data class Complex(val namespace: String, val path: String) {

    companion object {

        @IncludedCodec(keyable = true)
        val CODEC = Codec.STRING.xmap(
            { Complex(it.split(":").first(), it.split(":").lastOrNull() ?: "") },
            { "${it.namespace}:${it.path}" }
        )

        @IncludedCodec(named = "cumulative_long_list")
        val CUMULATIVE_LONG_LIST: Codec<List<Long>> =
            Codec.LONG.listOf().xmap(
                { it.runningFold(0, Long::plus).distinct() },
                { it.reversed().runningFold(0, Long::minus).reversed() },
            )
    }
}

enum class TestEnum {
    A, B, C
}

@GenerateCodec
data class TestData(
    val name: String,
    @FieldName("t") val thing: Map<String, String>,
    @NamedCodec("cumulative_long_list") val cumLong: List<Long>,
    val list: MutableList<String>,
    val nullable: String = "",
    val complex: Complex = Complex("owdding", "test"),
    val complexMap: Map<Complex, Int>,
    val enumKeyMap: MutableMap<TestEnum, Int>,
) {

    companion object {

    }
}

@NamedCodec("test")
@GenerateCodec(generateLazy = true)
data class NotComplex(
    val test1: String = "",
    val complex2: Lazy<Complex2>,
    val test6: String = "",
)

@GenerateCodec
data class Complex2(
    val test1: String = "",
    val test2: String = "",
    val test3: String = "",
    val test4: String = "",
    val test5: String = "",
    val test: Set<String>  = emptySet()
)