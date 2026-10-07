package com.richardsenger.piratesnships.seachest;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The sea chest item (docs/design.md §11). Contents live in the {@code container} component like a shulker box's.
 * <ul>
 *   <li>Used on a block: placed as the block, except where the block would go into water (and not into a ship's
 *       plot): there it is launched as the floating {@link SeaChestEntity} with the contents.</li>
 *   <li>Used while looking at a water surface (nothing solid in reach): launched there as well.</li>
 *   <li>Used in the air: worn on the back (chest armour slot), swapped like armour; the armour slot takes it too.</li>
 * </ul>
 * With {@code sea_chest.enabled} off it is a plain block item: it goes into the main hand, never the chest slot, and
 * nothing floats.
 */
public class SeaChestItem extends BlockItem implements Equipable {

    public static final String WORN_HINT_KEY = "item." + Constants.MOD_ID + ".sea_chest.worn_hint";

    public SeaChestItem(Block block, Properties properties) {
        super(block, properties);
    }

    // --- wearing ------------------------------------------------------------------------------------------------

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return SeaChestConfig.ENABLED.get() ? EquipmentSlot.CHEST : EquipmentSlot.MAINHAND;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    // --- placing and launching ----------------------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (SeaChestConfig.ENABLED.get()) {
            BlockPlaceContext place = new BlockPlaceContext(context);
            BlockPos at = place.getClickedPos();
            Level level = context.getLevel();
            BlockState there = level.getBlockState(at);
            if (level.getFluidState(at).is(FluidTags.WATER) && there.canBeReplaced(place)) {
                if (level.isClientSide) {
                    return InteractionResult.SUCCESS;
                }
                // In a ship's plot (a flooded hull) the chest is a block like anywhere else on deck
                if (SableShips.containing(level, at) == null) {
                    Player player = context.getPlayer();
                    float yaw = player == null ? 0f : player.getYRot() + 180f;
                    launch((ServerLevel) level, Vec3.atBottomCenterOf(at), yaw, context.getItemInHand(), player);
                    return InteractionResult.CONSUME;
                }
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SeaChestConfig.ENABLED.get()) {
            return InteractionResultHolder.pass(stack);
        }
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() == HitResult.Type.BLOCK && level.getFluidState(hit.getBlockPos()).is(FluidTags.WATER)) {
            if (!level.isClientSide && SableShips.containing(level, hit.getBlockPos()) == null) {
                launch((ServerLevel) level, Vec3.atBottomCenterOf(hit.getBlockPos()), player.getYRot() + 180f, stack, player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    /** Spawns the floating chest with the stack's contents and name, and uses up one item (not in creative). */
    public static SeaChestEntity launch(ServerLevel level, Vec3 at, float yaw, ItemStack stack, @Nullable Player player) {
        SeaChestEntity chest = SeaChestEntity.fromItem(level, at, yaw, stack);
        level.addFreshEntity(chest);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 1.2f);
        stack.consume(1, player);
        return chest;
    }

    // --- contents -----------------------------------------------------------------------------------------------

    /** Another sea chest (and shulker boxes, through NeoForge's stack-aware check) can't take a sea chest. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        List<ItemStack> contents = SeaChestContents.nonEmpty(stack);
        int shown = 0;
        for (ItemStack s : contents) {
            if (shown == 5) break;
            lines.add(Component.translatable("container.shulkerBox.itemCount", s.getHoverName(), s.getCount()));
            shown++;
        }
        if (contents.size() > shown) {
            lines.add(Component.translatable("container.shulkerBox.more", contents.size() - shown).withStyle(ChatFormatting.ITALIC));
        }
        if (SeaChestConfig.ENABLED.get()) {
            lines.add(Component.translatable(WORN_HINT_KEY).withStyle(ChatFormatting.GRAY));
        }
    }
}
