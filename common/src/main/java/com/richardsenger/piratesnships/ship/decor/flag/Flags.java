package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import com.richardsenger.piratesnships.ship.decor.ShipDecor;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Flag content (docs/design.md §4.7): the three flag items, the flagpole block entity and the {@code flags} item tag.
 * A vanilla banner (any {@link BannerItem}, with or without patterns) counts as a custom flag.
 */
public final class Flags {

    public static final RegistryEntry<Item, Item> MERCHANT_FLAG = flag("merchant_flag");
    public static final RegistryEntry<Item, Item> NAVY_FLAG = flag("navy_flag");
    public static final RegistryEntry<Item, Item> JOLLY_ROGER_FLAG = flag("jolly_roger_flag");

    public static final RegistryEntry<BlockEntityType<?>, BlockEntityType<FlagpoleBlockEntity>> FLAGPOLE_BLOCK_ENTITY =
            ModRegistry.blockEntity("flagpole", FlagpoleBlockEntity::new, ShipDecor.FLAGPOLE);

    /** Everything that can be hoisted on a flagpole: our flags and {@code #minecraft:banners}. */
    public static final TagKey<Item> FLAGS = TagKey.create(Registries.ITEM, Constants.id("flags"));

    private Flags() {
    }

    public static List<RegistryEntry<Item, Item>> flagItems() {
        return List.of(MERCHANT_FLAG, NAVY_FLAG, JOLLY_ROGER_FLAG);
    }

    /** The flag kind an item is, or null if it can't be hoisted (banners only while custom banner flags are enabled). */
    public static @Nullable FlagKind kindOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(MERCHANT_FLAG.get())) return FlagKind.MERCHANT;
        if (stack.is(NAVY_FLAG.get())) return FlagKind.NAVY;
        if (stack.is(JOLLY_ROGER_FLAG.get())) return FlagKind.JOLLY_ROGER;
        if (stack.getItem() instanceof BannerItem && FlagConfig.CUSTOM_BANNER_FLAGS.get()) return FlagKind.CUSTOM;
        return null;
    }

    /** A fresh item for a flag kind (for commands); a plain white banner for {@code CUSTOM}, empty for {@code NONE}. */
    public static ItemStack stackFor(FlagKind kind) {
        return switch (kind) {
            case NONE -> ItemStack.EMPTY;
            case MERCHANT -> new ItemStack(MERCHANT_FLAG.get());
            case NAVY -> new ItemStack(NAVY_FLAG.get());
            case JOLLY_ROGER -> new ItemStack(JOLLY_ROGER_FLAG.get());
            case CUSTOM -> new ItemStack(Items.WHITE_BANNER);
        };
    }

    private static RegistryEntry<Item, Item> flag(String name) {
        return ModRegistry.item(name, () -> new Item(new Item.Properties().stacksTo(16)));
    }

    public static void init() {
    }
}
