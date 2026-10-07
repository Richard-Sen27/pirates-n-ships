package com.richardsenger.piratesnships.sailing.helm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The helm's wheel (docs/design.md §5.3, HELM1): its angle in degrees (positive = turned clockwise as the helmsman sees
 * it = starboard). Saved with the block, so it moves with the ship and survives reloads and disassembly; synced to
 * clients for {@code client/HelmWheelRenderer}. The rudder follows it ({@link WheelMath#rudderAngle}), read by the
 * sailing runtime from {@link com.richardsenger.piratesnships.sailing.ship.ShipControls#setWheel}.
 *
 * <p>On the client the last two received angles and the time of the last update are kept, so the renderer can ease
 * from one to the next over one tick.
 */
public class HelmBlockEntity extends BlockEntity {

    static final String TAG_WHEEL = "wheel";
    /** Easing time of the client's wheel between two updates: one tick. */
    public static final long EASE_NANOS = 50_000_000L;

    private float wheel;
    // client: the angle shown before the last update and when that update arrived (System.nanoTime)
    private float previousWheel;
    private long updatedAt;

    public HelmBlockEntity(BlockPos pos, BlockState state) {
        super(HelmContent.HELM_ENTITY.get(), pos, state);
    }

    /** The wheel angle in degrees (positive = starboard). */
    public float wheel() {
        return wheel;
    }

    /** Sets the wheel angle; on the server a change is saved and sent to clients. */
    public void setWheel(double degrees) {
        float next = (float) degrees;
        if (next == wheel) {
            return;
        }
        wheel = next;
        if (level != null && !level.isClientSide) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Client: the wheel angle to draw now, eased over {@code easeNanos} from the previous to the current angle. */
    public float displayedWheel(long now, long easeNanos) {
        if (easeNanos <= 0 || updatedAt == 0L) {
            return wheel;
        }
        double t = Math.min(1.0, Math.max(0.0, (now - updatedAt) / (double) easeNanos));
        return (float) (previousWheel + (wheel - previousWheel) * t);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level.isClientSide) {
            // a block entity that arrives after its section was meshed (a helm from before HELM1) is only drawn after
            // the section is meshed again
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat(TAG_WHEEL, wheel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        float next = tag.getFloat(TAG_WHEEL);
        if (level != null && level.isClientSide) {
            long now = System.nanoTime();
            previousWheel = updatedAt == 0L ? next : displayedWheel(now, EASE_NANOS);
            updatedAt = now;
        }
        wheel = Float.isNaN(next) ? 0f : next;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat(TAG_WHEEL, wheel);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
