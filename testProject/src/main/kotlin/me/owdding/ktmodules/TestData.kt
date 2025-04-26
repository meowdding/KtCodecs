package me.owdding.ktmodules

import com.mojang.serialization.Codec
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
    val thing: Map<String, String>,
    val list: List<String>,
    val nullable: String = "",
    val complex: Complex = Complex("owdding", "test"),
    val complexMap: Map<Complex, Int>
) {

    companion object {

    }
}