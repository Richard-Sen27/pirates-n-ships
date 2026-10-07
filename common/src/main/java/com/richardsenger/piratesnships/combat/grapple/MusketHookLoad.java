package com.richardsenger.piratesnships.combat.grapple;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.combat.firearms.FirearmContent;
import com.richardsenger.piratesnships.combat.firearms.FirearmKind;
import com.richardsenger.piratesnships.combat.firearms.FirearmLoad;
import com.richardsenger.piratesnships.combat.firearms.FirearmService;
import com.richardsenger.piratesnships.combat.firearms.FirearmsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The grappling hook as a musket load (GR3, docs/design.md §8.3 "Launching"): offered when the musket's other hand
 * holds a hook in a valid arrangement ({@link GrappleLaunch#launcherHand}). The firearm's own loading session runs
 * (hold, the {@code musket_reload} pose stretched to the reload ticks, the white bar); when it completes one gunpowder
 * is burnt (not in creative, nor with {@code firearms.consume_gunpowder} off) and the hook moves from the other hand
 * into the musket ({@link GrappleContent#LOADED_HOOK}). Firing it ({@code FirearmService.fire}: cooldown, rain misfire,
 * shot sound, smoke, recoil) launches a {@link GrapplingHookEntity} at the musket's speed and rope length; a misfire
 * keeps the hook in the musket.
 */
public final class MusketHookLoad implements FirearmLoad {

    public static final MusketHookLoad INSTANCE = new MusketHookLoad();

    public static final String DESCRIPTION_KEY = "item." + Constants.MOD_ID + ".firearm.loaded_hook";
    public static final String OCCUPIED_KEY = "message." + Constants.MOD_ID + ".grapple.musket_loaded";
    public static final String NO_POWDER_KEY = "message." + Constants.MOD_ID + ".grapple.no_powder";

    private MusketHookLoad() {
    }

    @Override
    public boolean offered(LivingEntity shooter, InteractionHand gunHand, ItemStack gun, FirearmKind kind) {
        return kind == FirearmKind.MUSKET && shooter instanceof Player
                && GrapplingHookItem.launcherHand(shooter) == gunHand;
    }

    private static GrappleLaunch.Powder powder(Player player) {
        return GrappleLaunch.powder(player.hasInfiniteMaterials(), player.getInventory().countItem(Items.GUNPOWDER),
                FirearmsConfig.CONSUME_GUNPOWDER.get());
    }

    @Override
    public boolean canLoad(LivingEntity shooter, InteractionHand gunHand, ItemStack gun) {
        return shooter instanceof Player player && GrappleLaunch.musketRefusal(FirearmContent.isLoaded(gun),
                GrappleContent.isHookLoaded(gun), powder(player)) == GrappleLaunch.MusketRefusal.NONE;
    }

    @Override
    public boolean load(Level level, LivingEntity shooter, InteractionHand gunHand, ItemStack gun) {
        if (!(shooter instanceof Player player) || !canLoad(shooter, gunHand, gun)) return false;
        InteractionHand hookHand = GrappleLaunch.other(gunHand);
        ItemStack hook = player.getItemInHand(hookHand);
        if (!(hook.getItem() instanceof GrapplingHookItem)) return false;
        GrappleLaunch.Powder powder = powder(player);
        boolean take = !player.hasInfiniteMaterials();
        gun.set(GrappleContent.LOADED_HOOK.get(), new LoadedHook(hook.copyWithCount(1), take));
        if (take) {
            hook.shrink(1);
        }
        if (powder == GrappleLaunch.Powder.CONSUME) {
            FirearmService.consumeOne(player.getInventory(), Items.GUNPOWDER);
        }
        return true;
    }

    @Override
    public boolean isIn(ItemStack gun) {
        return GrappleContent.isHookLoaded(gun);
    }

    @Override
    public void launch(ServerLevel level, LivingEntity shooter, ItemStack gun, FirearmKind kind, float xRot, float yRot) {
        LoadedHook loaded = GrappleContent.loadedHook(gun);
        gun.remove(GrappleContent.LOADED_HOOK.get());
        if (loaded == null || !(shooter instanceof Player player)) return;
        GrappleService.launchHook(level, player, loaded.hook(), loaded.taken(), GrappleLaunch.Mode.MUSKET);
    }

    @Override
    public @Nullable Component describe(ItemStack gun) {
        return Component.translatable(DESCRIPTION_KEY);
    }

    @Override
    public @Nullable Component missingMessage() {
        return Component.translatable(NO_POWDER_KEY);
    }

    @Override
    public @Nullable Component occupiedMessage() {
        return Component.translatable(OCCUPIED_KEY);
    }
}
