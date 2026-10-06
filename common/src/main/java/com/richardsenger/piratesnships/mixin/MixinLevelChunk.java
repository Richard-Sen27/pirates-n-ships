package com.richardsenger.piratesnships.mixin;

import com.richardsenger.piratesnships.ship.hull.runtime.HullRuntimes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tells the hull runtimes about every block state change, so a ship notices doors opening, hull blocks being broken
 * (breaches) and anything else that changes its hull (docs/design.md §4.2, §4.5).
 *
 * <p>Why a mixin: there is no event for "a block state changed" that covers every path ({@code Level#setBlock} from
 * players, explosions, pistons, commands, Sable's own block moves). Vanilla has none, NeoForge's {@code BlockEvent}s cover
 * only player and some world actions, and Fabric has none. Sable learns about plot changes the same way, through its own
 * {@code LevelChunk#setBlockState} mixin ({@code refs/sable/.../mixin/plot/LevelChunkMixin.java}), and it publishes no
 * listener: {@code SableCommonEvents#handleBlockChange} and {@code LevelPlot#onBlockChange} call fixed internals only.
 *
 * <p>{@code setBlockState} returns the previous state, or null when nothing changed. Server levels on the server thread
 * only; {@link HullRuntimes#onBlockChanged} returns at once when the level has no ship runtimes.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void pirates_n_ships$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving,
                                                 CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        if (old != null && old != state && ((LevelChunk) (Object) this).getLevel() instanceof ServerLevel level
                && level.getServer().isSameThread()) {
            HullRuntimes.onBlockChanged(level, pos, old, state);
        }
    }
}
