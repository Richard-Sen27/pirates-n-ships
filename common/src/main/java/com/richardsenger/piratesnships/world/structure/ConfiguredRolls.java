package com.richardsenger.piratesnships.world.structure;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.world.WorldConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.providers.number.NumberProviders;

import java.util.Set;

/**
 * Number provider {@code pirates_n_ships:configured_rolls} (STR6): the rolls of a pool of the port structures' loot
 * tables ({@link StructureLoot}), scaled by the server config's {@code world.structures.loot_rolls}
 * ({@link WorldConfig#STRUCTURE_LOOT_ROLLS}) when the chest is first opened.
 *
 * <pre>{@code
 * "rolls": {"type": "pirates_n_ships:configured_rolls", "base": {"type": "minecraft:uniform", "min": 2, "max": 4}}
 * }</pre>
 *
 * <p>The base is drawn first, then multiplied; a fractional result rounds up with the probability of its fraction
 * ({@link #scale}), so a scale of 0.5 on one roll gives the roll half the time and the average follows the scale.
 */
public record ConfiguredRolls(NumberProvider base) implements NumberProvider {

    public static final MapCodec<ConfiguredRolls> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProviders.CODEC.fieldOf("base").forGetter(ConfiguredRolls::base)
    ).apply(i, ConfiguredRolls::new));

    /** {@code base} rolls scaled by {@code factor}; the fraction rounds up when {@code unit} (in [0, 1)) is below it. */
    public static int scale(int base, double factor, double unit) {
        if (base <= 0 || factor <= 0) return 0;
        double scaled = base * factor;
        int whole = Mth.floor(scaled);
        return whole + (unit < scaled - whole ? 1 : 0);
    }

    @Override
    public int getInt(LootContext context) {
        return scale(base.getInt(context), WorldConfig.STRUCTURE_LOOT_ROLLS.get(), context.getRandom().nextDouble());
    }

    @Override
    public float getFloat(LootContext context) {
        return getInt(context);
    }

    @Override
    public LootNumberProviderType getType() {
        return PortStructures.CONFIGURED_ROLLS.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return base.getReferencedContextParams();
    }
}
