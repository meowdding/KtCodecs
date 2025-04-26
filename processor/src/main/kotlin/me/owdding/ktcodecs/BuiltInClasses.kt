package me.owdding.ktcodecs

import com.squareup.kotlinpoet.ClassName

private const val SERIALIZATION = "com.mojang.serialization"
private const val DATAFIXER = "com.mojang.datafixers"

internal val CODEC_TYPE = ClassName(SERIALIZATION, "Codec")
internal val RECORD_CODEC_BUILDER_TYPE = ClassName("$SERIALIZATION.codecs", "RecordCodecBuilder")
internal val EITHER_TYPE = ClassName("$DATAFIXER.util", "Either")

internal val MUTABLE_MAP = ClassName("kotlin.collections", "MutableMap")
internal val MUTABLE_SET = ClassName("kotlin.collections", "MutableSet")
internal val MUTABLE_LIST = ClassName("kotlin.collections", "MutableList")
