package me.owdding.ktmodules

import com.mojang.serialization.Codec
import me.owdding.ktcodecs.FieldName
import me.owdding.ktcodecs.GenerateCodec
import me.owdding.ktcodecs.IncludedCodec

data class Complex(val namespace: String, val path: String) {

    companion object {

        @IncludedCodec(keyable = true)
        val CODEC = Codec.STRING.xmap(
            { Complex(it.split(":").first(), it.split(":").lastOrNull() ?: "") },
            { "${it.namespace}:${it.path}" }
        )
    }
}

@GenerateCodec
data class TestData(
    val name: String,
    @FieldName("t") val thing: Map<String, String>,
    val list: List<String>,
    val nullable: String = "",
    val complex: Complex = Complex("owdding", "test"),
    val complexMap: Map<Complex, Int>
) {

    companion object {

    }
}

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
    val test6: String = "",
    val test7: String = "",
)