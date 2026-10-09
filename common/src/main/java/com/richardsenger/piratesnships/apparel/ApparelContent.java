package com.richardsenger.piratesnships.apparel;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.EnumMap;
import java.util.List;

/**
 * The wearable apparel (design.md §9, §15): hats that copy the hat of a seafarer mob, the officer's coat, and the pirate
 * captain's coat, breeches and boots (ART9).
 */
public final class ApparelContent {

    /** A black tricorn with a skull on the front: the soldier's hat in pirate colours. */
    public static final RegistryEntry<Item, HatItem> PIRATE_HAT = hat("pirate_hat");
    /** The pirate's red bandana with its knot and tails. */
    public static final RegistryEntry<Item, HatItem> BANDANA = hat("bandana");
    /** The navy soldier's tricorn, edged in white, with a black cockade. */
    public static final RegistryEntry<Item, HatItem> NAVY_HAT = hat("navy_hat");
    /** The navy officer's bicorne, worn athwart, edged in gold, with a gold loop and cockade. */
    public static final RegistryEntry<Item, HatItem> OFFICER_HAT = hat("officer_hat");
    /**
     * The pirate captain's hat (ART6): wide black brim edged in gold and cocked up on the left, red band, white plume.
     * Not craftable: the named captain (BOS1) wears it and drops it.
     */
    public static final RegistryEntry<Item, HatItem> CAPTAINS_HAT = hat("captains_hat");

    /** Every hat. */
    public static final List<RegistryEntry<Item, HatItem>> HATS = List.of(PIRATE_HAT, BANDANA, NAVY_HAT, OFFICER_HAT, CAPTAINS_HAT);
    /** The hats with a crafting recipe (the captain's hat is a drop). */
    public static final List<RegistryEntry<Item, HatItem>> CRAFTED_HATS = List.of(PIRATE_HAT, BANDANA, NAVY_HAT, OFFICER_HAT);

    /**
     * The officer's coat's armour material: only its layer texture ({@code textures/models/armor/officers_coat_layer_1.png})
     * and equip sound matter; the armour points come from the config ({@link ClothingItem}).
     */
    public static final RegistryEntry<ArmorMaterial, ArmorMaterial> OFFICERS_COAT_MATERIAL = material("officers_coat", Items.BLUE_WOOL);

    /** The navy officer's coat (ART6): blue, white facings, gold epaulettes and lace, red cuffs; chest slot, tails (ART9). */
    public static final RegistryEntry<Item, CoatItem> OFFICERS_COAT = ModRegistry.item("officers_coat",
            () -> new CoatItem(OFFICERS_COAT_MATERIAL.holder(), ApparelConfig.OFFICERS_COAT_ARMOR, new Item.Properties().stacksTo(1)));

    /**
     * The captain's coat's material (ART9): {@code captains_coat_layer_1.png}, drawn with the tails model. A material of
     * its own because the coat's texture keeps the tails in the head area of layer 1, where the boots' layer 1 would
     * need nothing but the legs.
     */
    public static final RegistryEntry<ArmorMaterial, ArmorMaterial> CAPTAINS_COAT_MATERIAL = material("captains_coat", Items.BLACK_WOOL);
    /**
     * The captain's breeches and boots (ART9): {@code captains_clothing_layer_2.png} (breeches, vanilla's inner armour
     * model) and {@code captains_clothing_layer_1.png} (boots, vanilla's outer armour model).
     */
    public static final RegistryEntry<ArmorMaterial, ArmorMaterial> CAPTAINS_CLOTHING_MATERIAL = material("captains_clothing", Items.LEATHER);

    /**
     * The pirate captain's coat (ART9): charcoal, open front edged in gold over a brocade waistcoat, brass buttons, a red
     * sash on the left hip, a leather baldric over the right shoulder, crimson cuffs; knee-long tails. Chest slot.
     */
    public static final RegistryEntry<Item, CoatItem> CAPTAINS_COAT = ModRegistry.item("captains_coat",
            () -> new CoatItem(CAPTAINS_COAT_MATERIAL.holder(), ApparelConfig.CAPTAINS_COAT_ARMOR, new Item.Properties().stacksTo(1)));
    /** The pirate captain's dark breeches (ART9); legs slot. */
    public static final RegistryEntry<Item, ClothingItem> CAPTAINS_BREECHES = ModRegistry.item("captains_breeches",
            () -> new ClothingItem(CAPTAINS_CLOTHING_MATERIAL.holder(), ArmorItem.Type.LEGGINGS, ApparelConfig.CAPTAINS_BREECHES_ARMOR,
                    new Item.Properties().stacksTo(1)));
    /** The pirate captain's bucket-top boots (ART9); feet slot. */
    public static final RegistryEntry<Item, ClothingItem> CAPTAINS_BOOTS = ModRegistry.item("captains_boots",
            () -> new ClothingItem(CAPTAINS_CLOTHING_MATERIAL.holder(), ArmorItem.Type.BOOTS, ApparelConfig.CAPTAINS_BOOTS_ARMOR,
                    new Item.Properties().stacksTo(1)));

    /** The captain's clothing that he drops ({@code mobs.captain.clothing_drop_chance} each), in drop order. */
    public static final List<RegistryEntry<Item, ? extends ClothingItem>> CAPTAINS_CLOTHING = List.of(CAPTAINS_COAT, CAPTAINS_BREECHES, CAPTAINS_BOOTS);
    /** Every clothing item (not the hats). */
    public static final List<RegistryEntry<Item, ? extends ClothingItem>> CLOTHING = List.of(OFFICERS_COAT, CAPTAINS_COAT, CAPTAINS_BREECHES, CAPTAINS_BOOTS);

    private ApparelContent() {
    }

    /**
     * A clothing material: only its layer textures ({@code textures/models/armor/<name>_layer_1.png}, {@code _layer_2}
     * for the legs slot) and the equip sound matter; defence 0 in every slot, the points come from the config.
     */
    private static RegistryEntry<ArmorMaterial, ArmorMaterial> material(String name, Item repair) {
        return Services.REGISTRY.register(Registries.ARMOR_MATERIAL, name, () -> new ArmorMaterial(
                Util.make(new EnumMap<>(ArmorItem.Type.class), m -> {
                    for (ArmorItem.Type type : ArmorItem.Type.values()) m.put(type, 0);
                }),
                15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(repair),
                List.of(new ArmorMaterial.Layer(Constants.id(name))), 0f, 0f));
    }

    private static RegistryEntry<Item, HatItem> hat(String name) {
        return ModRegistry.item(name, () -> new HatItem(new Item.Properties().stacksTo(1)));
    }

    public static void init() {
    }
}
