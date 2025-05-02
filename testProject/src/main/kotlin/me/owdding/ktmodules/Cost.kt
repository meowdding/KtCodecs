package me.owdding.ktmodules

import me.owdding.ktcodecs.GenerateCodec
import me.owdding.ktcodecs.GenerateDispatchCodec
import me.owdding.ktcodecs.generated.DispatchHelper
import kotlin.reflect.KClass

@GenerateDispatchCodec(Cost::class)
enum class CostTypes(override val type: KClass<out Cost>): DispatchHelper<Cost> {
    COINS(CoinCost::class),
    ITEM(ItemCost::class),
    ESSENCE(EssenceCost::class),
    ;
}

enum class Essence

@GenerateCodec
data class EssenceCost(val essenceType: Essence, val amount: Int) : Cost(CostTypes.ITEM)
@GenerateCodec
data class ItemCost(val itemId: String, val amount: Int) : Cost(CostTypes.ITEM)
@GenerateCodec
data class CoinCost(val amount: Int) : Cost(CostTypes.COINS)

abstract class Cost(val type: CostTypes)
