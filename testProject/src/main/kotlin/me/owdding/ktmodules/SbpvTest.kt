package me.owdding.ktmodules

import com.mojang.serialization.Codec
import me.owdding.ktcodecs.IncludedCodec

object SbpvTest {

    @IncludedCodec(named = "cum_int_list_alt")
    val CUMULATIVE_INT_LIST_ALT: Codec<List<Int>> =
        Codec.INT.listOf().xmap(
            { it.runningFold(0, Int::plus).drop(1) },
            { it.reversed().runningFold(0, Int::minus).reversed() },
        )

    @IncludedCodec(named = "cum_int_long_map")
    val INT_LONG_MAP: Codec<Map<Int, Long>> = Codec.unboundedMap(Codec.STRING, Codec.LONG).xmap(
        { it.mapKeys { entry -> entry.key.toInt() } },
        { it.mapKeys { entry -> entry.key.toString() } },
    )

    @IncludedCodec(named = "cum_int_list")
    val CUMULATIVE_INT_LIST: Codec<List<Int>> =
        Codec.INT.listOf().xmap(
            { it.runningFold(0, Int::plus).distinct() },
            { it.reversed().runningFold(0, Int::minus).reversed() },
        )

    @IncludedCodec(named = "cum_long_list")
    val CUMULATIVE_LONG_LIST: Codec<List<Long>> =
        Codec.LONG.listOf().xmap(
            { it.runningFold(0, Long::plus).distinct() },
            { it.reversed().runningFold(0, Long::minus).reversed() },
        )

    data class Vector2i(val x: Int, val y: Int)

    @IncludedCodec(named = "vec_2i")
    val VECTOR_2I: Codec<Vector2i> = Codec.INT.listOf(2, 2).xmap(
        { Vector2i(it[0], it[1]) },
        { listOf(it.x, it.y) },
    )

    data class Component(val string: String)

    @IncludedCodec(named = "component_tag")
    val COMPONENT_TAG: Codec<Component> = Codec.STRING.xmap(
        { Component(it) },
        { it.string },
    )

    @IncludedCodec(named = "cum_string_int_map")
    val CUMULATIVE_STRING_INT_MAP: Codec<List<Map<String, Int>>> = Codec.unboundedMap(Codec.STRING, Codec.INT).listOf().xmap(
        {
            it.runningFold(
                mutableMapOf(),
            ) { acc: MutableMap<String, Int>, mutableMap: MutableMap<String, Int>? ->
                LinkedHashMap(
                    acc.also {
                        mutableMap?.forEach {
                            acc[it.key] = it.value + (acc[it.key] ?: 0)
                        }
                    },
                )
            }.drop(1)
        },
        { it },
    )

    data class ItemStack(val resourceLocation: String)

    @IncludedCodec(named = "lazy_item_ref")
    val ITEM_REFERENCE: Codec<Lazy<ItemStack>> = Codec.STRING.xmap(
        { lazy { ItemStack(it) } },
        { it.value.resourceLocation },
    )
}