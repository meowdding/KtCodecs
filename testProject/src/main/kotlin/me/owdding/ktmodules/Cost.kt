package me.owdding.ktmodules

import me.owdding.ktcodecs.*
import me.owdding.ktcodecs.IntRange
import me.owdding.ktcodecs.generated.DispatchHelper
import org.jetbrains.annotations.Range
import java.util.*
import kotlin.reflect.KClass

@GenerateDispatchCodec(Cost::class, "cost")
enum class CostTypes(override val type: KClass<out Cost>) : DispatchHelper<Cost> {
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
data class ItemCost(
    @CustomGetterMethod @Lenient @FieldNames("item_id", "item_id2", "item_id3") val itemId: String = "",
    @CustomGetterMethod @FieldNames("amount1", "amount2", "amount3") val amount: @Range(from = 1, to = 2) Int
) : Cost(CostTypes.ITEM) {

    fun serializeItemId() = if (itemId.isEmpty()) Optional.empty() else Optional.of(itemId)
    fun serializeAmount() = amount
}

@GenerateCodec
data class CoinCost(@IntRange(min = 0) val amount: Int) : Cost(CostTypes.COINS)

abstract class Cost(val type: CostTypes)
