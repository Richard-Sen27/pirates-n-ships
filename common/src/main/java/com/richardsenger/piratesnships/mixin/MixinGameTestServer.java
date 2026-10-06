package com.richardsenger.piratesnships.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps the headless GameTest server's tests within {@value #RANGE} blocks of the world origin.
 *
 * <p>Why: vanilla places the whole test grid at a random X/Z of up to ±14,999,992 ({@code GameTestServer#startTests}).
 * Sable's native physics (Rapier, {@code refs/sable/sable_rapier/src/main/rust/marten/src/lib.rs}: {@code Real = f32})
 * loses collision precision that far out (one f32 step is a whole block beyond 8,388,608), and ships resting on stone
 * sank into it or fell through the world: the 72 m/s "MOVING" ship of spike 2 was in free fall. Reproduced with ships
 * on identical stone pads at fixed positions: none moved at 1,000, 3,000,000 or 6,000,000, some sank at 12,000,000
 * and 13,600,000. A random position within the range still gives every run a fresh area in the persisted test world.
 *
 * <p>Why a mixin: the test position is a local inside {@code startTests} with no event, config or loader hook.
 * {@code GameTestServer} is only loaded by the GameTest server, so this never affects a real game.
 */
@Mixin(GameTestServer.class)
public abstract class MixinGameTestServer {

    @Unique
    private static final int RANGE = 250_000;

    @Redirect(method = "startTests", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int pirates_n_ships$nearOrigin(RandomSource random, int min, int max) {
        return random.nextIntBetweenInclusive(-RANGE, RANGE);
    }
}
