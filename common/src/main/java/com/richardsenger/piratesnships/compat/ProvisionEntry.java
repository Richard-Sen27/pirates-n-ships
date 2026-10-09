package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.crew.provisions.ProvisionClassifier;
import com.richardsenger.piratesnships.crew.provisions.ProvisionSettings;
import com.richardsenger.piratesnships.crew.provisions.ProvisionType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One item as the "Ship's Provisions" page of a recipe viewer shows it (JEI1, docs/compat.md): what it counts as in a
 * ship's pantry or water barrel (design.md §7.4), classified by the same rules the crew uses
 * ({@link ProvisionClassifier}).
 *
 * @param stack          the item (a water bottle is a potion stack)
 * @param type           what it counts as
 * @param shelfLifeDays  days until it spoils in the pantry, 0 when it never spoils (or spoilage is off)
 */
public record ProvisionEntry(ItemStack stack, ProvisionType type, double shelfLifeDays) {

    /**
     * Every item that counts as a provision: the default stack of every registered item, plus a water bottle. Needs
     * item tags (a running server, or a client after the tag sync).
     */
    public static List<ProvisionEntry> all(ProvisionSettings settings) {
        List<ItemStack> candidates = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) candidates.add(new ItemStack(item));
        }
        candidates.add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        List<ProvisionEntry> out = new ArrayList<>();
        for (ItemStack stack : candidates) {
            of(stack, settings).ifPresent(out::add);
        }
        return out;
    }

    public static Optional<ProvisionEntry> of(ItemStack stack, ProvisionSettings settings) {
        return ProvisionClassifier.classify(stack, settings).map(t -> new ProvisionEntry(stack, t,
                settings.spoilageEnabled() && t.perishable() ? t.shelfLifeTicks() / (double) ProvisionSettings.TICKS_PER_DAY : 0));
    }

    /** The text lines beside the slot, top to bottom. */
    public List<TextLine> lines() {
        return lines(type, shelfLifeDays);
    }

    /** The lines for a provision type; {@code shelfLifeDays} 0 = never spoils. Pure. */
    public static List<TextLine> lines(ProvisionType type, double shelfLifeDays) {
        List<TextLine> out = new ArrayList<>();
        String value = MarketEntry.number(type.valuePerUnit());
        switch (type.kind()) {
            case FOOD -> out.add(TextLine.of(CompatText.PROVISION_FOOD, value));
            case WATER -> out.add(TextLine.of(CompatText.PROVISION_WATER, value));
            case RUM -> out.add(TextLine.of(CompatText.PROVISION_RUM, value));
        }
        if (shelfLifeDays > 0) {
            out.add(TextLine.of(CompatText.PROVISION_SPOILS, MarketEntry.number(shelfLifeDays)));
        } else {
            out.add(TextLine.of(CompatText.PROVISION_PRESERVED));
        }
        if (type.antiScurvy()) out.add(TextLine.of(CompatText.PROVISION_ANTI_SCURVY));
        out.add(TextLine.of(CompatText.PROVISION_WEIGHT, MarketEntry.number(type.weightPerUnit())));
        return out;
    }
}
