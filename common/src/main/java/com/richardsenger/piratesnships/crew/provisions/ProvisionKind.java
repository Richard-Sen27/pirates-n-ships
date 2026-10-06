package com.richardsenger.piratesnships.crew.provisions;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a provision is good for (design.md §7.4). */
public enum ProvisionKind implements StringRepresentable {
    /** Food. Its value per unit is nutrition (vanilla hunger points). */
    FOOD,
    /** Fresh water. Its value per unit is water rations (one ration waters one crew member for a day at rate 1). */
    WATER,
    /** Rum. Its value per unit is rum rations. */
    RUM;

    public static final Codec<ProvisionKind> CODEC = StringRepresentable.fromEnum(ProvisionKind::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
