package me.owdding.ktcodecs

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder

@GenerateCodec
data class Generic2<T>(
    val string: String,
    val generic: T,
)

class ComponentThing(val string: String) : ComponentLike {
    override fun asString() = string
}

enum class ComponentEnum : ComponentLike {
    MEOW,
    MRRRRP,
    ;
    override fun asString() = name
}

interface ComponentLike {
    fun asString(): String
}

@GenerateCodec
data class MultipleGenerics<T, Other>(
    val first: T?,
    val mrrrrrrp: Int,
    val second: Other,
    val list: List<T>,
    @Compact val compactList: MutableList<Other>,
    val mapWithInnerCompactSet: Map<String, @Compact MutableSet<T>>,
) where T : ComponentLike, T : Enum<T>

@GenerateCodec
data class Generic3<T : ComponentLike>(
    val meow: String,
    val generic: T,
) {
    companion object {
        @IncludedCodec
        val GENERIC3_STRING_CODEC: Codec<Generic3<ComponentThing>> = RecordCodecBuilder.create {
            it.group(
                Codec.STRING.fieldOf("meow").forGetter { getter -> getter.meow },
                Codec.STRING.xmap({ ComponentThing(it) }, { it.asString() }).fieldOf("generic").forGetter { getter -> getter.generic },
            ).apply(it) { p_meow, p_generic ->
                Generic3(p_meow, p_generic)
            }
        }
    }
}