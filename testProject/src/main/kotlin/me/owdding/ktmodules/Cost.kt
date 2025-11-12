package me.owdding.ktmodules

import me.owdding.ktcodecs.*
import me.owdding.ktcodecs.IntRange
import me.owdding.ktcodecs.generated.DispatchHelper
import org.jetbrains.annotations.Range
import kotlin.reflect.KClass

@GenerateDispatchCodec(Cost::class, "cost")
enum class CostTypes(override val type: KClass<out Cost>): DispatchHelper<Cost> {
    COINS(CoinCost::class),
    ITEM(ItemCost::class),
    ESSENCE(EssenceCost::class),
    ;
    companion object {
        fun getType(name: String) = valueOf(name)
    }
}

enum class Essence

@GenerateCodec
data class EssenceCost(val essenceType: Essence, val amount: Int) : Cost(CostTypes.ITEM)
@GenerateCodec
data class ItemCost(@Lenient @FieldNames("item_id", "item_id2", "item_id3") val itemId: String = "", @FieldNames("amount1", "amount2", "amount3")  val amount: @Range(from = 1, to = 2) Int) : Cost(CostTypes.ITEM)
@GenerateCodec
data class CoinCost(@IntRange(min = 0) val amount: Int) : Cost(CostTypes.COINS)

abstract class Cost(val type: CostTypes)
