package me.owdding.ktcodecs.utils

internal data class GenerateCodecData(
    val generateLazy: Boolean,
    val generateDefault: Boolean,
    val createCodecMethod: Boolean,
)