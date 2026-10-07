package com.richardsenger.piratesnships.ship.decor;

import com.richardsenger.piratesnships.combat.content.ContentTestSupport;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

import java.util.Collection;
import java.util.List;

/** Registration, drops, facing and recipes of the {@code ship.decor} module. */
public final class ShipDecorGameTests {

    public static final List<String> ITEM_IDS = List.of("merchant_flag", "navy_flag", "jolly_roger_flag");
    public static final List<String> BLOCK_IDS = List.of("figurehead_mermaid", "figurehead_lion", "figurehead_eagle", "figurehead_skull",
            "nameplate", "flagpole", "cargo_crate", "cargo_barrel");

    private ShipDecorGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipDecorGameTests.class);
    }

    @ModGameTest
    public static void decorBlocksAreRegistered(GameTestHelper helper) {
        ContentTestSupport.assertRegistered(helper, List.of(), BLOCK_IDS);
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void decorBlocksPlaceAndDropThemselves(GameTestHelper helper) {
        List<Block> blocks = List.of(ShipDecor.FIGUREHEAD_MERMAID.get(), ShipDecor.FIGUREHEAD_LION.get(), ShipDecor.FIGUREHEAD_EAGLE.get(),
                ShipDecor.FIGUREHEAD_SKULL.get(), ShipDecor.NAMEPLATE.get(), ShipDecor.FLAGPOLE.get(),
                ShipDecor.CARGO_CRATE.get(), ShipDecor.CARGO_BARREL.get());
        // The nameplate hangs on a wall: give every block a backing block on its north side
        for (int i = 0; i < blocks.size(); i++) {
            BlockPos pos = new BlockPos(i, 1, 2);
            helper.setBlock(pos.north(), net.minecraft.world.level.block.Blocks.OAK_PLANKS);
            ContentTestSupport.assertPlacesAndDropsSelf(helper, blocks.get(i), pos);
        }
        helper.succeed();
    }

    @ModGameTest
    public static void figureheadsKeepTheirFacing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        for (RegistryEntry<Block, FigureheadBlock> f : ShipDecor.figureheads()) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                helper.setBlock(pos, f.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d));
                helper.assertBlockProperty(pos, HorizontalDirectionalBlock.FACING, d);
            }
        }
        helper.succeed();
    }

    /**
     * A player clicking the side of a hull block mounts the figurehead with its plate on that block: the figure looks away
     * from the clicked block, toward the player, whichever way the player looks. Clicking the top of a block turns the figure
     * toward the player.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void figureheadsMountOnTheClickedBlock(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int x = 0;
        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos hull = new BlockPos(1 + 2 * x, 2, 4);
            x++;
            helper.setBlock(hull, Blocks.OAK_PLANKS);
            // The player stands in front of the clicked face and looks at the hull, i.e. against the face's normal
            player.setYRot(face.getOpposite().toYRot());
            BlockPos placed = place(helper, player, hull, face);
            helper.assertBlockProperty(placed, HorizontalDirectionalBlock.FACING, face);
            // Looking sideways at the same face changes nothing: the plate still sits on the clicked block
            helper.setBlock(placed, Blocks.AIR);
            player.setYRot(face.getClockWise().toYRot());
            helper.assertBlockProperty(place(helper, player, hull, face), HorizontalDirectionalBlock.FACING, face);
        }
        for (Direction looking : Direction.Plane.HORIZONTAL) {
            BlockPos floor = new BlockPos(1 + 2 * looking.get2DDataValue(), 1, 7);
            helper.setBlock(floor, Blocks.STONE);
            player.setYRot(looking.toYRot());
            helper.assertBlockProperty(place(helper, player, floor, Direction.UP), HorizontalDirectionalBlock.FACING,
                    looking.getOpposite());
        }
        helper.succeed();
    }

    private static BlockPos place(GameTestHelper helper, Player player, BlockPos clicked, Direction face) {
        ItemStack stack = new ItemStack(ShipDecor.FIGUREHEAD_LION.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(clicked);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)), face, abs, false);
        InteractionResult r = ((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
        helper.assertTrue(r.consumesAction(), "placing the figurehead on " + face + " failed: " + r);
        BlockPos placed = clicked.relative(face);
        helper.assertBlockPresent(ShipDecor.FIGUREHEAD_LION.get(), placed);
        return placed;
    }

    @ModGameTest
    public static void decorRecipesAreLoaded(GameTestHelper helper) {
        for (String id : List.of("figurehead_mermaid", "figurehead_lion", "figurehead_eagle", "figurehead_skull", "nameplate",
                "cargo_crate", "cargo_barrel")) {
            ContentTestSupport.assertRecipe(helper, id, net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    com.richardsenger.piratesnships.Constants.id(id)), 1);
        }
        ContentTestSupport.assertRecipe(helper, "flagpole", ShipDecor.FLAGPOLE.get().asItem(), 2);
        helper.succeed();
    }
}
