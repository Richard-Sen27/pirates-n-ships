package com.richardsenger.piratesnships.ship.decor.wood.client;

import com.richardsenger.piratesnships.platform.event.ClientEvents;
import com.richardsenger.piratesnships.ship.decor.wood.ShipWood;
import com.richardsenger.piratesnships.ship.decor.wood.WoodBlock;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Renderer parts of a wood family (WOOD1, docs/woods.md): a block entity renderer that draws a stand-alone part
 * (like the helm's wheel) draws the part of the member's wood, {@code block/<wood>_<part>}, the child datagen writes
 * with {@link WoodFamily#childModels}. {@link #register} loads the part's child for every wood of the family through
 * {@link ClientEvents#registerAdditionalModel}; {@link #get} fetches the baked one for a member's wood.
 */
public final class WoodModels {

    private WoodModels() {
    }

    /** The model ids of {@code parent}'s children for {@code woods}, in order (pure). */
    public static List<ResourceLocation> ids(Collection<ShipWood> woods, ResourceLocation parent) {
        List<ResourceLocation> out = new ArrayList<>(woods.size());
        for (ShipWood wood : woods) out.add(WoodFamily.child(wood, parent));
        return out;
    }

    /** Registers {@code parent}'s child for every wood of {@code family} as a stand-alone model (from {@code initClient()}). */
    public static void register(WoodFamily<?> family, ResourceLocation parent) {
        for (ResourceLocation id : ids(family.woods(), parent)) ClientEvents.registerAdditionalModel(id);
    }

    /** The baked child of {@code parent} for {@code wood} (client thread, after model loading). */
    public static BakedModel get(ShipWood wood, ResourceLocation parent) {
        return ClientEvents.additionalModel(WoodFamily.child(wood, parent));
    }

    /** The baked child of {@code parent} for the wood of {@code block}. */
    public static BakedModel get(WoodBlock block, ResourceLocation parent) {
        return get(block.wood(), parent);
    }
}
