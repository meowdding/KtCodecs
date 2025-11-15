package me.owdding.ktcodecs.utils

import me.owdding.kotlinpoet.ClassName
import me.owdding.kotlinpoet.asClassName
import java.util.*

private const val SERIALIZATION = "com.mojang.serialization"
private const val DATAFIXER = "com.mojang.datafixers"

internal val CODEC_TYPE = ClassName(SERIALIZATION, "Codec")
internal val LAZY = Lazy::class.asClassName()
internal val MAP_CODEC_TYPE = ClassName(SERIALIZATION, "MapCodec")
internal val RECORD_CODEC_BUILDER_TYPE = ClassName("$SERIALIZATION.codecs", "RecordCodecBuilder")
internal val EITHER_TYPE = ClassName("$DATAFIXER.util", "Either")

internal val MUTABLE_MAP = MutableMap::class.asClassName()
internal val MUTABLE_SET = MutableSet::class.asClassName()
internal val MUTABLE_LIST = MutableList::class.asClassName()

internal val COLLECTION = Collection::class.asClassName()
internal val MAP = Map::class.asClassName()
internal val LIST = List::class.asClassName()
internal val SET = Set::class.asClassName()

internal val ENUM_MAP = EnumMap::class.asClassName()
internal val ENUM_SET = Set::class.asClassName()

internal val INT = Int::class.asClassName()
internal val DOUBLE = Double::class.asClassName()
internal val FLOAT = Float::class.asClassName()
internal val LONG = Long::class.asClassName()