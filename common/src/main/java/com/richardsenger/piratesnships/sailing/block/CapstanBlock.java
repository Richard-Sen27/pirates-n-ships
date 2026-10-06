package com.richardsenger.piratesnships.sailing.block;

import com.richardsenger.piratesnships.sailing.force.AnchorState;
import com.richardsenger.piratesnships.sailing.ship.ShipControls;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The capstan (docs/design.md §5.3, §6): on an assembled ship, using it drops the ship's anchor, and using it again
 * raises it. The anchor state lives with the ship (see {@code ShipControls}); {@link #ANCHOR} only shows its phase and
 * is set by the runtime.
 */
public class CapstanBlock extends Block {

    /** Block-state mirror of {@link AnchorState.Phase}. */
    public enum Phase implements StringRepresentable {
        RAISED, DROPPING, HOLDING, RAISING;

        public static Phase of(AnchorState.Phase p) {
            return values()[p.ordinal()];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Phase> ANCHOR = EnumProperty.create("anchor", Phase.class);

    public CapstanBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ANCHOR, Phase.RAISED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ANCHOR);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            player.displayClientMessage(ShipControls.useCapstan(serverLevel, pos), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
