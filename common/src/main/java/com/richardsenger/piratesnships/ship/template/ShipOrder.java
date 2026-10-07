package com.richardsenger.piratesnships.ship.template;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * A ship ordered from a seafarer village's shipwright (design.md §4.1, SW1), kept on its port
 * ({@code world.port.Port#orders}) until it is picked up. Only a record: the ship is placed at pickup.
 * Days are world days ({@code dayTime / 24000}, fractional).
 */
public record ShipOrder(UUID id, ResourceLocation template, UUID owner, double orderedDay, double finishDay) {

    public static final Codec<ShipOrder> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ShipOrder::id),
            ResourceLocation.CODEC.fieldOf("template").forGetter(ShipOrder::template),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(ShipOrder::owner),
            Codec.DOUBLE.fieldOf("ordered_day").forGetter(ShipOrder::orderedDay),
            Codec.DOUBLE.fieldOf("finish_day").forGetter(ShipOrder::finishDay)
    ).apply(i, ShipOrder::new));

    public ShipOrder withFinishDay(double day) {
        return new ShipOrder(id, template, owner, orderedDay, day);
    }

    /** The first eight hex digits of the id, as commands show it. */
    public String shortId() {
        return shortId(id);
    }

    public static String shortId(UUID id) {
        return id.toString().substring(0, 8).toLowerCase(Locale.ROOT);
    }

    /** Whether {@code typed} names this order: its full id or a prefix of at least four characters. */
    public boolean matches(String typed) {
        String t = typed.toLowerCase(Locale.ROOT);
        return t.length() >= 4 && id.toString().startsWith(t);
    }
}
