package com.richardsenger.piratesnships.ship.template;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.ship.ShipTestCleanup;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.LocalBlock;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.Outcome;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.Result;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Ship templates: the starter sloop's structure resolves to real blocks, the place command puts it on the water in
 * front of a (mock) player with the waterline at the surface, refuses obstructions and unknown templates, and
 * {@code assemble} makes a Sable ship from it.
 */
public final class ShipTemplateGameTests {

    private static final String BATCH = "pirates_n_ships_ship_template";
    /** Basin water from y=2 up to this row (the surface). */
    private static final int SURFACE = 7;
    private static final ResourceLocation SLOOP = ShipTemplates.STARTER_SLOOP_ID;

    private ShipTemplateGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipTemplateGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * 40×40 stone basin (floor y=1, walls at the edges) with water y=2..7, and the GameTest barrier ceiling (y=13)
     * removed because the sloop's mast reaches far above the template.
     */
    private static void basin(GameTestHelper h) {
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                boolean wall = x == 0 || x == 39 || z == 0 || z == 39;
                for (int y = 2; y <= 8; y++) {
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE : y <= SURFACE ? Blocks.WATER : Blocks.AIR);
                }
                BlockPos ceiling = new BlockPos(x, 13, z);
                if (h.getBlockState(ceiling).is(Blocks.BARRIER)) {
                    h.setBlock(ceiling, Blocks.AIR);
                }
            }
        }
    }

    /** A mock operator standing at test-relative {@code feet}, looking {@code facing}. */
    private static CommandSourceStack operator(GameTestHelper h, BlockPos feet, Direction facing) {
        Player player = h.makeMockPlayer(GameType.CREATIVE);
        BlockPos abs = h.absolutePos(feet);
        player.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, facing.toYRot(), 0);
        return player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
    }

    private static int run(GameTestHelper h, CommandSourceStack source, String command) {
        try {
            return h.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("command failed to parse: " + command, e);
        }
    }

    private static StructureTemplate sloopStructure(GameTestHelper h) {
        ShipTemplate t = ShipTemplates.TYPE.server().require(SLOOP);
        return ShipTemplatePlacer.structure(h.getLevel(), t)
                .orElseThrow(() -> new IllegalStateException("structure " + t.structure() + " not found"));
    }

    private static List<LocalBlock> sloopBlocks(GameTestHelper h) {
        return ShipTemplatePlacer.blocks(sloopStructure(h), h.getLevel().holderLookup(Registries.BLOCK));
    }

    /** Every template block is in the world at origin + rotation (block type; shapes such as fences may connect). */
    private static void assertPlaced(GameTestHelper h, List<LocalBlock> blocks, Rotation rotation, BlockPos origin) {
        for (LocalBlock b : blocks) {
            BlockPos world = TemplatePlacement.toWorld(b.pos(), rotation, origin);
            Block found = h.getLevel().getBlockState(world).getBlock();
            if (found != b.state().getBlock()) {
                h.fail("template block " + b.pos() + " (" + b.state() + ") missing at " + world + ": found " + found);
            }
        }
    }

    // ------------------------------------------------------------------ tests

    /** Every palette entry of the converted sloop names a registered block and only properties that block has. */
    @ModGameTest(batch = BATCH)
    public static void starterSloopPaletteResolves(GameTestHelper h) {
        CompoundTag saved = sloopStructure(h).save(new CompoundTag());
        ListTag palette = saved.getList(StructureTemplate.PALETTE_TAG, Tag.TAG_COMPOUND);
        h.assertTrue(palette.size() > 10, "sloop palette has only " + palette.size() + " states");
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            ResourceLocation name = ResourceLocation.parse(entry.getString("Name"));
            h.assertTrue(BuiltInRegistries.BLOCK.containsKey(name), "unknown block " + name + " in the sloop");
            Block block = BuiltInRegistries.BLOCK.get(name);
            CompoundTag props = entry.getCompound("Properties");
            for (String key : props.getAllKeys()) {
                var property = block.getStateDefinition().getProperty(key);
                h.assertTrue(property != null, name + " has no property " + key);
                h.assertTrue(property.getValue(props.getString(key)).isPresent(), name + "[" + key + "=" + props.getString(key) + "] is invalid");
            }
        }
        List<LocalBlock> blocks = sloopBlocks(h);
        BlockPos helm = ShipTemplatePlacer.helm(ShipTemplates.STARTER_SLOOP, blocks);
        h.assertTrue(blocks.stream().anyMatch(b -> b.pos().equals(helm) && b.state().is(AssemblyContent.HELM.get())),
                "the sloop's helm position " + helm + " holds no helm");
        h.succeed();
    }

    /** {@code /pirates ship place starter_sloop} facing north: bow away from the player, waterline on the surface, dry hold. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH)
    public static void placeSloopFacingNorth(GameTestHelper h) {
        basin(h);
        BlockPos feet = new BlockPos(20, 8, 37);
        CommandSourceStack source = operator(h, feet, Direction.NORTH);
        int ok = run(h, source, "pirates ship place starter_sloop");
        h.assertTrue(ok == 1, "place returned " + ok);

        StructureTemplate structure = sloopStructure(h);
        int waterline = ShipTemplates.STARTER_SLOOP.waterlineFor(null);
        BlockPos origin = TemplatePlacement.origin(structure.getSize(), Rotation.NONE, Direction.NORTH, h.absolutePos(feet),
                ShipTemplatePlacer.GAP, h.absolutePos(new BlockPos(0, SURFACE, 0)).getY(), waterline);
        assertPlaced(h, sloopBlocks(h), Rotation.NONE, origin);
        h.assertTrue(origin.getY() + waterline == h.absolutePos(new BlockPos(0, SURFACE, 0)).getY(), "waterline not at the surface");
        // the stern is nearest to the player: the template's last row ends GAP blocks ahead of them
        h.assertTrue(origin.getZ() + structure.getSize().getZ() - 1 == h.absolutePos(feet).getZ() - ShipTemplatePlacer.GAP,
                "stern not " + ShipTemplatePlacer.GAP + " blocks ahead of the player");
        BlockPos helm = TemplatePlacement.toWorld(ShipTemplates.STARTER_SLOOP.helm().orElseThrow(), Rotation.NONE, origin);
        h.assertTrue(h.getLevel().getBlockState(helm).is(AssemblyContent.HELM.get()), "no helm at " + helm);
        // The hold (template x 3, y 2, z 10) lies below the waterline and is drained.
        BlockPos hold = TemplatePlacement.toWorld(new BlockPos(3, 2, 10), Rotation.NONE, origin);
        h.assertTrue(h.getLevel().getBlockState(hold).isAir(), "hold at " + hold + " is " + h.getLevel().getBlockState(hold));
        // and the sea outside the hull is untouched
        BlockPos beside = TemplatePlacement.toWorld(new BlockPos(-1, 2, 10), Rotation.NONE, origin);
        h.assertTrue(h.getLevel().getBlockState(beside).is(Blocks.WATER), "sea beside the hull is gone");
        h.succeed();
    }

    /** Facing east with {@code assemble}: rotated, assembled from the helm the definition names, all blocks gathered. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH, timeoutTicks = 200)
    public static void placeAndAssembleSloopFacingEast(GameTestHelper h) {
        basin(h);
        BlockPos feet = new BlockPos(2, 8, 20);
        Result r = ShipTemplateCommands.place(operator(h, feet, Direction.EAST), SLOOP, false, true);
        AssemblyResult a = r.assembly();
        if (a != null && a.shipId() != null) {
            ShipTestCleanup.track(h, a.shipId());
        }
        h.assertTrue(r.outcome() == Outcome.PLACED, "outcome " + r.outcome() + " at " + r.where());
        h.assertTrue(r.rotation() == Rotation.CLOCKWISE_90, "rotation " + r.rotation());
        BlockPos expectedHelm = TemplatePlacement.toWorld(ShipTemplates.STARTER_SLOOP.helm().orElseThrow(), Rotation.CLOCKWISE_90, r.origin());
        h.assertTrue(expectedHelm.equals(r.helm()), "helm at " + r.helm() + ", definition says " + expectedHelm);
        h.assertTrue(r.surfaceY() == h.absolutePos(new BlockPos(0, SURFACE, 0)).getY() && !r.seaLevel(), "surface " + r.surfaceY());
        h.assertTrue(a != null && a.outcome() == AssemblyResult.Outcome.ASSEMBLED, "assembly " + (a == null ? null : a.outcome()));
        h.assertTrue(a.count() == r.blocks(), "assembled " + a.count() + " of " + r.blocks() + " template blocks");
        h.assertTrue(h.getLevel().getBlockState(r.helm()).isAir(), "helm still in the world after assembly");
        h.succeed();
    }

    /** A stone block where the hull would go refuses the placement and places nothing; force places anyway. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, batch = BATCH)
    public static void obstructedPlacementRefuses(GameTestHelper h) {
        basin(h);
        BlockPos feet = new BlockPos(20, 8, 37);
        StructureTemplate structure = sloopStructure(h);
        BlockPos origin = TemplatePlacement.origin(structure.getSize(), Rotation.NONE, Direction.NORTH, h.absolutePos(feet),
                ShipTemplatePlacer.GAP, h.absolutePos(new BlockPos(0, SURFACE, 0)).getY(), ShipTemplates.STARTER_SLOOP.waterlineFor(null));
        BlockPos keel = TemplatePlacement.toWorld(new BlockPos(4, 0, 10), Rotation.NONE, origin);
        h.getLevel().setBlockAndUpdate(keel, Blocks.STONE.defaultBlockState());

        Result r = ShipTemplateCommands.place(operator(h, feet, Direction.NORTH), SLOOP, false, false);
        h.assertTrue(r.outcome() == Outcome.OBSTRUCTED && keel.equals(r.where()), "outcome " + r.outcome() + " at " + r.where());
        BlockPos helm = TemplatePlacement.toWorld(ShipTemplates.STARTER_SLOOP.helm().orElseThrow(), Rotation.NONE, origin);
        h.assertTrue(h.getLevel().getBlockState(helm).isAir(), "something was placed despite the obstruction");

        Result forced = ShipTemplateCommands.place(operator(h, feet, Direction.NORTH), SLOOP, true, false);
        h.assertTrue(forced.outcome() == Outcome.PLACED, "forced outcome " + forced.outcome());
        BlockState keelState = h.getLevel().getBlockState(keel);
        h.assertTrue(!keelState.is(Blocks.STONE), "force did not replace the stone");
        h.succeed();
    }

    /** An unknown template id refuses, through the dispatcher and through the placer. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = BATCH)
    public static void unknownTemplateRefuses(GameTestHelper h) {
        CommandSourceStack source = operator(h, new BlockPos(4, 1, 4), Direction.NORTH);
        h.assertTrue(run(h, source, "pirates ship place no_such_ship") == 0, "unknown template did not refuse");
        Result r = ShipTemplateCommands.place(source, Constants.id("no_such_ship"), true, true);
        h.assertTrue(r.outcome() == Outcome.UNKNOWN_TEMPLATE, "outcome " + r.outcome());
        h.assertTrue(ShipTemplateCommands.resolve(ResourceLocation.parse("starter_sloop")).equals(SLOOP), "bare id not resolved to ours");
        h.assertTrue(run(h, source, "pirates ship templates") >= 1, "templates lists nothing");
        h.succeed();
    }
}
