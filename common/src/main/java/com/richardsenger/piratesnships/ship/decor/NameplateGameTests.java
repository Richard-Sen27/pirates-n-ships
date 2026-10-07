package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import com.richardsenger.piratesnships.ship.sable.SableShips;
import com.richardsenger.piratesnships.ship.sable.ShipBody;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The ship's name on nameplates (design.md §4.8, G8): naming and renaming an assembled ship, a plate placed on an
 * already named ship, disassembly (the plate is cleared) and the {@code ship_identity.nameplate_shows_name} toggle.
 * Test hulls stand on stone (terrain, never gathered) like {@code AssemblyGameTests}.
 */
public final class NameplateGameTests {

    private static final BlockPos HELM = new BlockPos(4, 3, 4);
    /** On the north edge of the deck, hanging from the plank south of it, facing north. */
    private static final BlockPos PLATE = new BlockPos(4, 2, 2);
    /** {@link #PLATE} relative to {@link #HELM}. */
    private static final BlockPos PLATE_FROM_HELM = PLATE.subtract(HELM);

    private NameplateGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(NameplateGameTests.class);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void namingAndRenamingAShipUpdatesItsNameplate(GameTestHelper helper) {
        buildHull(helper, true);
        ShipBody ship = assemble(helper);
        BlockPos plate = find(ship, ShipDecor.NAMEPLATE.get());
        helper.assertTrue(plate != null, "nameplate not assembled with the ship");
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(text(helper, plate).isEmpty(), "an unnamed ship's plate shows '" + text(helper, plate) + "'");
                    helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
                })
                .thenWaitUntil(() -> assertText(helper, plate, "Black Gull"))
                .thenExecute(() -> helper.assertTrue(ShipAssembler.name(ship, "Sea Wolf").success(), "renaming failed"))
                .thenWaitUntil(() -> assertText(helper, plate, "Sea Wolf"))
                .thenSucceed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void plateHungOnANamedShipShowsItsName(GameTestHelper helper) {
        buildHull(helper, false);
        ShipBody ship = assemble(helper);
        helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
        BlockPos helm = find(ship, AssemblyContent.HELM.get());
        helper.assertTrue(helm != null, "helm not in the plot");
        BlockPos plate = helm.offset(PLATE_FROM_HELM);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getBlockState(plate.south()).is(Blocks.OAK_PLANKS), "no deck plank to hang the plate on");
        level.setBlock(plate, plateState(), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockState(plate).is(ShipDecor.NAMEPLATE.get()), "plate not placed on the ship");
        helper.succeedWhen(() -> assertText(helper, plate, "Black Gull"));
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 300)
    public static void disassemblyClearsTheNameplate(GameTestHelper helper) {
        buildHull(helper, true);
        ShipBody ship = assemble(helper);
        UUID id = ship.id();
        BlockPos plotPlate = find(ship, ShipDecor.NAMEPLATE.get());
        helper.assertTrue(plotPlate != null, "nameplate not assembled with the ship");
        helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
        AtomicReference<BlockPos> worldPlate = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> assertText(helper, plotPlate, "Black Gull"))
                .thenWaitUntil(() -> {
                    ShipBody s = SableShips.byId(helper.getLevel(), id);
                    helper.assertTrue(s != null, "ship vanished before disassembly");
                    AssemblyResult r = ShipAssembler.disassemble(s, find(s, AssemblyContent.HELM.get()), null);
                    if (!r.success()) throw new GameTestAssertException("not yet disassembled: " + r.outcome());
                })
                .thenExecute(() -> {
                    // The ship may have settled a little: look for the plate within a block of its old spot
                    for (BlockPos d : BlockPos.betweenClosed(-1, -1, -1, 1, 1, 1)) {
                        if (helper.getBlockState(PLATE.offset(d)).is(ShipDecor.NAMEPLATE.get())) worldPlate.set(helper.absolutePos(PLATE.offset(d)));
                    }
                    helper.assertTrue(worldPlate.get() != null, "nameplate not back near " + PLATE);
                })
                .thenWaitUntil(() -> assertText(helper, worldPlate.get(), ""))
                .thenSucceed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200, batch = "pirates_n_ships_config_decor_nameplate")
    public static void plateStaysEmptyWhenTheToggleIsOff(GameTestHelper helper) {
        ConfigOverrides.during(helper, ShipIdentityConfig.NAMEPLATE_SHOWS_NAME, false);
        buildHull(helper, true);
        ShipBody ship = assemble(helper);
        BlockPos plate = find(ship, ShipDecor.NAMEPLATE.get());
        helper.assertTrue(plate != null, "nameplate not assembled with the ship");
        helper.assertTrue(ShipAssembler.name(ship, "Black Gull").success(), "naming failed");
        // Longer than a refresh interval (20 ticks by default)
        helper.runAfterDelay(50, () -> {
            assertText(helper, plate, "");
            if (helper.getLevel().getBlockEntity(plate) instanceof NameplateBlockEntity be) be.refresh();
            assertText(helper, plate, "");
            helper.succeed();
        });
    }

    // ------------------------------------------------------------------ helpers

    /** A 3×3 plank deck on stone with the helm in the middle and, if {@code withPlate}, a nameplate on its north edge. */
    private static void buildHull(GameTestHelper helper, boolean withPlate) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        }
        for (int x = 3; x <= 5; x++) {
            for (int z = 3; z <= 5; z++) helper.setBlock(new BlockPos(x, 2, z), Blocks.OAK_PLANKS);
        }
        helper.setBlock(HELM, AssemblyContent.HELM.get());
        if (withPlate) helper.setBlock(PLATE, plateState());
    }

    private static BlockState plateState() {
        return ShipDecor.NAMEPLATE.get().defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH);
    }

    private static ShipBody assemble(GameTestHelper helper) {
        AssemblyResult r = ShipTestCleanup.assemble(helper, HELM);
        helper.assertTrue(r.success() && r.shipId() != null, "assembly failed: " + r);
        ShipBody ship = SableShips.byId(helper.getLevel(), r.shipId());
        helper.assertTrue(ship != null, "no sub-level for " + r.shipId());
        return ship;
    }

    private static String text(GameTestHelper helper, BlockPos absolute) {
        if (!(helper.getLevel().getBlockEntity(absolute) instanceof NameplateBlockEntity be)) {
            throw new GameTestAssertException("no nameplate block entity at " + absolute);
        }
        return be.text();
    }

    private static void assertText(GameTestHelper helper, BlockPos absolute, String expected) {
        String actual = text(helper, absolute);
        if (!actual.equals(expected)) throw new GameTestAssertException("plate shows '" + actual + "', expected '" + expected + "'");
    }

    private static BlockPos find(ShipBody ship, Block block) {
        for (BlockPos p : ship.plotBlocks()) {
            if (ship.level().getBlockState(p).is(block)) return p;
        }
        return null;
    }
}
