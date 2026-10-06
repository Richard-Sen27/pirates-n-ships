package com.richardsenger.piratesnships.trade.market;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a port trades: its kind, climate, the seed its roles were derived from, and the role of every good it knows.
 * Goods missing from {@link #roles} count as {@link GoodRole#NOT_TRADED} until {@link #extendedWith} adds them.
 */
public record PortProfile(PortKind kind, Climate climate, long seed, Map<ResourceLocation, GoodRole> roles) {

    public static final Codec<PortProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            PortKind.CODEC.fieldOf("kind").forGetter(PortProfile::kind),
            Climate.CODEC.fieldOf("climate").forGetter(PortProfile::climate),
            Codec.LONG.fieldOf("seed").forGetter(PortProfile::seed),
            Codec.unboundedMap(ResourceLocation.CODEC, GoodRole.CODEC).fieldOf("roles").forGetter(PortProfile::roles)
    ).apply(i, PortProfile::new));

    public PortProfile {
        roles = Map.copyOf(roles);
    }

    public GoodRole role(ResourceLocation good) {
        return roles.getOrDefault(good, GoodRole.NOT_TRADED);
    }

    /** Goods with the given role, sorted by id. */
    public List<ResourceLocation> goodsWith(GoodRole role) {
        List<ResourceLocation> out = new ArrayList<>();
        roles.forEach((id, r) -> {
            if (r == role) out.add(id);
        });
        out.sort(Comparator.comparing(ResourceLocation::toString));
        return out;
    }

    /**
     * Adds roles for goods this profile doesn't know yet (e.g. added by a datapack after the port was created), rolled
     * the same way {@link ProfileDeriver} rolls them. Existing roles never change. Returns {@code this} if nothing is new.
     */
    public PortProfile extendedWith(Definitions<TradeGood> goods) {
        Map<ResourceLocation, GoodRole> added = null;
        for (Map.Entry<ResourceLocation, TradeGood> e : goods.all().entrySet()) {
            if (roles.containsKey(e.getKey())) continue;
            if (added == null) added = new HashMap<>(roles);
            added.put(e.getKey(), ProfileDeriver.roll(kind, climate, seed, e.getKey(), e.getValue()));
        }
        return added == null ? this : new PortProfile(kind, climate, seed, added);
    }
}
