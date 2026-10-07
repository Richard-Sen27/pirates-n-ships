package com.richardsenger.piratesnships.trade.cargo;

import com.richardsenger.piratesnships.trade.TradeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.function.DoubleSupplier;

/**
 * The {@code load} block-state property of our containers (cargo crate and barrel, pantry, water barrel; design.md
 * §4.9, CW1) and its debounced update. The property is physics only: the block states point every {@code load} value
 * at the same model, and datagen ({@link CargoPhysicsData}) gives each value its Sable mass, so Sable updates mass,
 * centre of mass and inertia on every state change ({@code SubLevelPhysicsSystem#updateMassDataFromBlockChange},
 * l.517-546). Mass per state: {@link CargoMass}.
 *
 * <p>A block entity owns one {@link Tracker}, marks it on every content change and ticks it on the server: the state is
 * rewritten at most once per {@link #DEBOUNCE_TICKS} and only when the value changes, and rechecked every
 * {@link #RECHECK_TICKS} so a changed config ({@code cargo_weight_affects_ships}, {@code weight_factor}) reaches blocks
 * nobody touches.
 */
public final class CargoLoad {

    public static final IntegerProperty LOAD = IntegerProperty.create("load", 0, CargoMass.MAX_LOAD);

    /** Smallest gap between two state updates of one block [ticks]. */
    public static final int DEBOUNCE_TICKS = 4;
    /** Interval of the config recheck [ticks]. */
    public static final int RECHECK_TICKS = 100;

    private CargoLoad() {
    }

    /** The {@code load} value a block's content asks for under the current config. */
    public static int stateFor(double weight, CargoMass.Profile profile) {
        return profile.loadState(weight, TradeConfig.cargoParams());
    }

    /**
     * Writes {@code load} if it differs (server only, block updates to clients only: comparators read the content, not
     * the state). Returns true if the state changed.
     */
    public static boolean apply(Level level, BlockPos pos, int load) {
        BlockState state = level.getBlockState(pos);
        if (level.isClientSide || !state.hasProperty(LOAD) || state.getValue(LOAD) == load) {
            return false;
        }
        return level.setBlock(pos, state.setValue(LOAD, load), Block.UPDATE_CLIENTS);
    }

    /** Debounce state of one container's {@code load}. Starts dirty so a placed, loaded or moved block catches up. */
    public static final class Tracker {
        private boolean dirty = true;
        private long nextUpdate = Long.MIN_VALUE;

        public void markDirty() {
            dirty = true;
        }

        public boolean isDirty() {
            return dirty;
        }

        /** Server tick: updates the state when due. {@code weight} is only evaluated when an update runs. */
        public void tick(Level level, BlockPos pos, CargoMass.Profile profile, DoubleSupplier weight) {
            long now = level.getGameTime();
            boolean recheck = Math.floorMod(now + pos.asLong(), RECHECK_TICKS) == 0;
            if (!(dirty || recheck) || now < nextUpdate) {
                return;
            }
            dirty = false;
            nextUpdate = now + DEBOUNCE_TICKS;
            apply(level, pos, stateFor(weight.getAsDouble(), profile));
        }
    }
}
