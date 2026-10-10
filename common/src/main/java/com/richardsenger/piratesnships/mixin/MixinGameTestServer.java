package com.richardsenger.piratesnships.mixin;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestGrid;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays the headless GameTest server's test grid out as a square centred on the world origin ({@link GameTestGrid}).
 *
 * <p>Why: vanilla places the grid at a random X/Z of up to ±14,999,992 ({@code GameTestServer#startTests}) and grows
 * it 8 tests per row towards +z. Sable's native physics (Rapier, {@code refs/sable/sable_rapier/src/main/rust/marten/
 * src/lib.rs}: {@code Real = f32}) loses precision with the distance from the origin: one f32 step is a whole block
 * beyond 8,388,608, where ships sank into stone or fell through the world (spike 2), and at 250,000 blocks a ship at
 * 0.3 m/s reported a velocity but did not move. Bounding the random corner to ±4,096 was not enough: the 8-wide grid of
 * about 1,300 tests is 4,900 blocks deep, so its last rows reached z 9,000, past 8,192 where an f32 step doubles to
 * 1/1024 block, and the helm tests there behaved differently (TST1: a ship pressed against a wall kept 0.3 m/s and was
 * never reported stuck; a course arrived after 400 ticks instead of 660). Which test ended up there depended on the
 * random corner and on the number of tests before it.
 *
 * <p>So the corner is no longer random: the number of tests per row is chosen for a square grid and the corner is put
 * at minus half its size, so every test of every run lies within about ±1,000 blocks. {@code ModGameTests} fails a
 * test that is placed outside {@link GameTestGrid#SAFE_RANGE} anyway.
 *
 * <p>Why a mixin: the test position and the grid's row length are locals of {@code startTests} with no event, config
 * or loader hook. {@code GameTestServer} is only loaded by the GameTest server, so this never affects a real game.
 */
@Mixin(GameTestServer.class)
public abstract class MixinGameTestServer {

    @Shadow
    private List<GameTestBatch> testBatches;

    @Unique
    private GameTestGrid.Plan pirates_n_ships$plan;
    @Unique
    private int pirates_n_ships$coordinate;

    @Inject(method = "startTests", at = @At("HEAD"))
    private void pirates_n_ships$planGrid(ServerLevel level, CallbackInfo ci) {
        List<int[]> sizes = new ArrayList<>();
        for (GameTestBatch batch : testBatches) {
            for (GameTestInfo info : batch.gameTestInfos()) {
                Vec3i size = level.getStructureManager().get(ResourceLocation.parse(info.getStructureName()))
                        .map(StructureTemplate::getSize).orElse(Vec3i.ZERO);
                boolean turned = info.getRotation() == Rotation.CLOCKWISE_90 || info.getRotation() == Rotation.COUNTERCLOCKWISE_90;
                sizes.add(turned ? new int[]{size.getZ(), size.getX()} : new int[]{size.getX(), size.getZ()});
            }
        }
        pirates_n_ships$plan = GameTestGrid.plan(sizes);
        pirates_n_ships$coordinate = 0;
        Constants.LOG.info("GameTest grid: {} tests, {} per row, {} x {} blocks from ({}, {})", sizes.size(),
                pirates_n_ships$plan.perRow(), pirates_n_ships$plan.width(), pirates_n_ships$plan.depth(),
                pirates_n_ships$plan.startX(), pirates_n_ships$plan.startZ());
    }

    /** The two random draws of the corner, x then z: the plan's corner instead. */
    @Redirect(method = "startTests", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextIntBetweenInclusive(II)I"))
    private int pirates_n_ships$nearOrigin(RandomSource random, int min, int max) {
        return pirates_n_ships$coordinate++ == 0 ? pirates_n_ships$plan.startX() : pirates_n_ships$plan.startZ();
    }

    /** Vanilla's 8 tests per row: the plan's row length instead. */
    @ModifyArg(method = "startTests", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/gametest/framework/StructureGridSpawner;<init>(Lnet/minecraft/core/BlockPos;IZ)V"), index = 1)
    private int pirates_n_ships$testsPerRow(int testsPerRow) {
        return pirates_n_ships$plan.perRow();
    }
}
