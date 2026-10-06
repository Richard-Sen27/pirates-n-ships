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
import net.minecraft.world.level.block.Block;
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
