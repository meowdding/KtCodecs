package me.owdding.ktcodecs.utils

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName

private const val SERIALIZATION = "com.mojang.serialization"
private const val DATAFIXER = "com.mojang.datafixers"

internal val CODEC_TYPE = ClassName(SERIALIZATION, "Codec")
internal val LAZY = Lazy::class.asClassName()
internal val MAP_CODEC_TYPE = ClassName(SERIALIZATION, "MapCodec")
internal val RECORD_CODEC_BUILDER_TYPE = ClassName("$SERIALIZATION.codecs", "RecordCodecBuilder")
internal val EITHER_TYPE = ClassName("$DATAFIXER.util", "Either")

internal val MUTABLE_MAP = ClassName("kotlin.collections", "MutableMap")
internal val MUTABLE_SET = ClassName("kotlin.collections", "MutableSet")
internal val MUTABLE_LIST = ClassName("kotlin.collections", "MutableList")

internal val INT = Int::class.asClassName()
internal val DOUBLE = Double::class.asClassName()
internal val FLOAT = Float::class.asClassName()
internal val LONG = Long::class.asClassName()