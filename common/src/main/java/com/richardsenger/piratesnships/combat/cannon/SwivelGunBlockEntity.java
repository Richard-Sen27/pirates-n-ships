package com.richardsenger.piratesnships.combat.cannon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * State of a swivel gun (docs/design.md §8.2, P2): the aim (yaw and elevation in degrees, block frame), the game time
 * the reload ends, the shot in the barrel, and the player aiming it right now. Aim and reload are saved with the block, so they move with the
 * ship; the aim is synced to clients for {@code client/SwivelGunRenderer}. The aiming player is not saved: a reload
 * lets go of the gun.
 */
public class SwivelGunBlockEntity extends BlockEntity {

    /** Smallest aim change in degrees that is sent to clients while a player turns the gun. */
    static final double SYNC_DEGREES = 0.5;

    private float yaw;
    private float elevation;
    private long reloadUntil;
    private @Nullable UUID operator;
    /** The shot in the barrel (item and count, as loaded), so a broken gun gives back what went in (Q2). */
    private ItemStack shot = ItemStack.EMPTY;

    public SwivelGunBlockEntity(BlockPos pos, BlockState state) {
        super(CannonContent.SWIVEL_GUN_ENTITY.get(), pos, state);
    }

    public SwivelRules.Aim aim() {
        return new SwivelRules.Aim(yaw, elevation);
    }

    public float yaw() {
        return yaw;
    }

    public float elevation() {
        return elevation;
    }

    /** Sets the aim; on the server a change of more than {@link #SYNC_DEGREES} is saved and sent to clients. */
    public void setAim(SwivelRules.Aim aim) {
        SwivelRules.Aim old = aim();
        yaw = (float) SwivelRules.wrapYaw(aim.yawDegrees());
        elevation = (float) aim.elevationDegrees();
        if (level != null && !level.isClientSide && aim.differs(old, SYNC_DEGREES)) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public long reloadUntil() {
        return reloadUntil;
    }

    public void setReloadUntil(long gameTime) {
        reloadUntil = gameTime;
        setChanged();
    }

    /** The shot in the barrel as it was loaded; empty when there is none (or it was loaded before Q2). */
    public ItemStack shot() {
        return shot;
    }

    public void setShot(ItemStack shot) {
        this.shot = shot.copy();
        setChanged();
    }

    /** The player aiming the gun, or null. */
    public @Nullable UUID operator() {
        return operator;
    }

    public void setOperator(@Nullable UUID operator) {
        this.operator = operator;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("yaw", yaw);
        tag.putFloat("elevation", elevation);
        tag.putLong("reload_until", reloadUntil);
        if (!shot.isEmpty()) {
            tag.put("shot", shot.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        yaw = tag.getFloat("yaw");
        elevation = tag.getFloat("elevation");
        reloadUntil = tag.getLong("reload_until");
        shot = tag.contains("shot") ? ItemStack.parseOptional(registries, tag.getCompound("shot")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("yaw", yaw);
        tag.putFloat("elevation", elevation);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
