package com.richardsenger.piratesnships.apparel;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * A wearable hat (design.md §9): right-click (or the inventory's head slot) puts it on the head, swapping with what was
 * there, like a helmet. Not an {@code ArmorItem}: vanilla's {@code CustomHeadLayer} then draws the item model itself
 * with its {@code head} display transform, so the hand-made 3D model sits on the head like the matching mob's hat.
 *
 * <p>The armour bonus comes from {@link #getDefaultAttributeModifiers()}, which vanilla (and NeoForge, through
 * {@code IItemExtension#getDefaultAttributeModifiers(ItemStack)}) asks whenever a stack has no
 * {@code attribute_modifiers} of its own, so it follows the config live.
 */
public class HatItem extends Item implements Equipable {

    public HatItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return HatArmor.SLOT;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return HatArmor.modifiers(ApparelConfig.HAT_ARMOR.get());
    }
}
