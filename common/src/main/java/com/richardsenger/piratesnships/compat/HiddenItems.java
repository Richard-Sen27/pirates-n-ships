package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.Constants;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Our items a recipe viewer leaves out of its item list (JEI1, docs/compat.md). Only items that are nothing without the
 * data the game writes onto them, so a blank one from the list could only confuse:
 * <ul>
 *   <li>{@code ship_receipt}: the shipwright's receipt for one ordered ship; blank, it names no ship and picks up
 *       nothing.</li>
 *   <li>{@code ship_commission}: the navy's grant of one ship to one player; blank, it names neither.</li>
 * </ul>
 * Everything else stays visible: blocks without an item of their own never appear in the list anyway (the hull patch,
 * map tile, hammock, ratlines, boarding plank, sea chest and almanac blocks are placed by items of the same id); the
 * bounty proof and the treasure map work blank (an empty proof is what the Almanac describes, a blank map binds itself
 * when used); spawn eggs are listed like vanilla's.
 */
public final class HiddenItems {

    public static final List<ResourceLocation> IDS = List.of(
            Constants.id("ship_receipt"),
            Constants.id("ship_commission"));

    private HiddenItems() {
    }

    public static boolean hidden(ResourceLocation item) {
        return IDS.contains(item);
    }
}
