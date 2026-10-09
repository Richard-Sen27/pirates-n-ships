package com.richardsenger.piratesnships.core.block;

import com.richardsenger.piratesnships.core.block.WaterloggingRules.Verdict;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WLOG1: the verdicts of {@link WaterloggingRules} and the {@link Waterlogging} helpers on vanilla blocks. Our own
 * blocks need a loader, so the registry walk itself is a GameTest ({@code WaterloggingGameTests}).
 */
class WaterloggingRulesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void partialBlocksMustBeWaterloggable() {
        assertEquals(Verdict.WATERLOGGABLE, WaterloggingRules.check("cleat", false, true));
        assertEquals(Verdict.MISSING, WaterloggingRules.check("cleat", false, false));
        assertTrue(WaterloggingRules.violates(Verdict.MISSING));
        assertFalse(WaterloggingRules.violates(Verdict.WATERLOGGABLE));
    }

    @Test
    void fullCubesNeedNothing() {
        assertEquals(Verdict.FULL_CUBE, WaterloggingRules.check("hull_patch", true, false));
        assertFalse(WaterloggingRules.violates(Verdict.FULL_CUBE));
        // a waterloggable full cube is allowed (vanilla has some), it just is not required
        assertEquals(Verdict.WATERLOGGABLE, WaterloggingRules.check("hull_patch", true, true));
    }

    @Test
    void exceptionsAreNamedAndKeptHonest() {
        assertTrue(WaterloggingRules.EXCEPTIONS.containsKey("brig_door"));
        WaterloggingRules.EXCEPTIONS.values().forEach(reason -> assertFalse(reason.isBlank(), "an exception without a reason"));
        assertEquals(Verdict.EXCEPTION, WaterloggingRules.check("brig_door", false, false));
        assertFalse(WaterloggingRules.violates(Verdict.EXCEPTION));
        // an exception that became waterloggable or a full cube must leave the list
        assertEquals(Verdict.STALE_EXCEPTION, WaterloggingRules.check("brig_door", false, true));
        assertEquals(Verdict.STALE_EXCEPTION, WaterloggingRules.check("brig_door", true, false));
        assertTrue(WaterloggingRules.violates(Verdict.STALE_EXCEPTION));
    }

    /** The brig door's reason holds: vanilla's doors are not waterloggable. */
    @Test
    void vanillaDoorsAreNotWaterloggable() {
        assertFalse(Blocks.OAK_DOOR instanceof SimpleWaterloggedBlock);
        assertFalse(Blocks.IRON_DOOR.defaultBlockState().hasProperty(Waterlogging.WATERLOGGED));
    }

    @Test
    void helpersFollowTheProperty() {
        BlockState dry = Blocks.OAK_SLAB.defaultBlockState();
        BlockState wet = dry.setValue(Waterlogging.WATERLOGGED, true);
        assertFalse(Waterlogging.isWaterlogged(dry));
        assertTrue(Waterlogging.isWaterlogged(wet));
        assertSame(Fluids.EMPTY, Waterlogging.fluid(dry, Fluids.EMPTY.defaultFluidState()).getType());
        assertTrue(Waterlogging.fluid(wet, Fluids.EMPTY.defaultFluidState()).isSource());
        assertSame(Fluids.WATER, Waterlogging.fluid(wet, Fluids.EMPTY.defaultFluidState()).getType());
        // a part cleared by code leaves its water, or air
        assertTrue(Waterlogging.leftBehind(wet).is(Blocks.WATER));
        assertTrue(Waterlogging.leftBehind(dry).isAir());
        // a block without the property is left alone
        assertFalse(Waterlogging.isWaterlogged(Blocks.STONE.defaultBlockState()));
        assertTrue(Waterlogging.leftBehind(Blocks.STONE.defaultBlockState()).isAir());
    }
}
