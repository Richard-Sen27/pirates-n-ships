package com.richardsenger.piratesnships.sailing.ship;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.sailing.SailingConfig;
import com.richardsenger.piratesnships.sailing.block.CleatBlockEntity;
import com.richardsenger.piratesnships.sailing.block.YardBlock;
import com.richardsenger.piratesnships.sailing.block.YardBlockEntity;
import com.richardsenger.piratesnships.sailing.force.SailTrim;
import com.richardsenger.piratesnships.sailing.sail.SailDecorations;
import com.richardsenger.piratesnships.sailing.sail.SailTint;
import com.richardsenger.piratesnships.sailing.sail.YardSails;
import com.richardsenger.piratesnships.sailing.ship.SailingGameTestsShips.Fixture;
import com.richardsenger.piratesnships.ship.assembly.AssemblyContent;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.assembly.ShipAssembler;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * SAIL2 GameTests (docs/design.md §4.8 "Sails: dyeable, and large sails can carry banner patterns"): dyeing a square
 * and a triangular sail, hanging a banner on a large square sail and taking it back, the size rule, the dye and banner
 * through a lengthened yard, a broken yard, assembly, reefing and disassembly, and the two server toggles (own
 * batches). "Client copy" means a fresh block entity loaded from the server's update tag, as a client gets it.
 */
public final class SailDyeGameTests {

    private SailDyeGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(SailDyeGameTests.class);
    }

    // ------------------------------------------------------------------ fixtures

    /**
     * A square sail on land at column (11, 11): a lower yard at y=3 of {@code lowerHalf}, a log and fence mast, and the
     * upper yard {@code drop} blocks higher of {@code upperHalf} (both along x). Returns the head, absolute.
     */
    private static BlockPos landSail(GameTestHelper h, int upperHalf, int lowerHalf, int drop) {
        for (int x = 4; x <= 18; x++) for (int z = 8; z <= 14; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
        h.setBlock(new BlockPos(11, 2, 11), Blocks.OAK_LOG);
        SailingGameTestsShips.yard(h, 11, 3, 11, lowerHalf, SailTrim.FURLED);
        for (int y = 4; y < 3 + drop; y++) h.setBlock(new BlockPos(11, y, 11), Blocks.OAK_FENCE);
        SailingGameTestsShips.yard(h, 11, 3 + drop, 11, upperHalf, SailTrim.FULL);
        return h.absolutePos(new BlockPos(11, 3 + drop, 11));
    }

    private static ItemInteractionResult useItem(GameTestHelper h, Player player, BlockPos abs, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockState s = h.getLevel().getBlockState(abs);
        return s.useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, hit(abs));
    }

    private static InteractionResult useEmpty(GameTestHelper h, Player player, BlockPos abs, boolean sneaking) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(sneaking);
        InteractionResult r = h.getLevel().getBlockState(abs).useWithoutItem(h.getLevel(), player, hit(abs));
        player.setShiftKeyDown(false);
        return r;
    }

    private static BlockHitResult hit(BlockPos abs) {
        return new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
    }

    private static YardBlockEntity yardBe(GameTestHelper h, BlockPos abs) {
        if (h.getLevel().getBlockEntity(abs) instanceof YardBlockEntity be) return be;
        throw new AssertionError("no yard block entity at " + abs);
    }

    /** A client's copy: a fresh block entity loaded from the update tag. */
    private static YardBlockEntity clientCopy(GameTestHelper h, YardBlockEntity be) {
        YardBlockEntity copy = new YardBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(be.getUpdateTag(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        return copy;
    }

    private static CleatBlockEntity cleatCopy(GameTestHelper h, CleatBlockEntity be) {
        CleatBlockEntity copy = new CleatBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.loadWithComponents(be.getUpdateTag(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        return copy;
    }

    /** A blue banner with a red saltire (vanilla's "cross") and a yellow border. */
    private static ItemStack crossBanner(GameTestHelper h) {
        var patterns = h.getLevel().registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
        ItemStack banner = new ItemStack(Items.BLUE_BANNER);
        banner.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
                .add(patterns.getOrThrow(BannerPatterns.CROSS), DyeColor.RED)
                .add(patterns.getOrThrow(BannerPatterns.BORDER), DyeColor.YELLOW).build());
        return banner;
    }

    // ------------------------------------------------------------------ dye

    /**
     * A dye on any block of the upper yard dyes the whole yard and is spent; the client copy of the head reads the dye's
     * tint; the same dye again is refused and kept; a yard that heads no sail (the lower one) refuses it; white is the
     * natural canvas.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40)
    public static void dyeColoursTheSquareSailAndReachesTheClient(GameTestHelper h) {
        BlockPos head = landSail(h, 1, 1, 3);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack red = new ItemStack(Items.RED_DYE, 3);
        ItemInteractionResult r = useItem(h, player, head.east(), red);
        h.assertTrue(r.consumesAction(), "dyeing did not take: " + r);
        h.assertTrue(red.getCount() == 2, "the dye was not spent: " + red.getCount());
        for (int dx = -1; dx <= 1; dx++) {
            h.assertTrue(yardBe(h, head.offset(dx, 0, 0)).dye() == DyeColor.RED, "yard block " + dx + " is not red");
        }
        YardBlockEntity copy = clientCopy(h, yardBe(h, head));
        h.assertTrue(copy.dye() == DyeColor.RED && copy.clothTint() == SailTint.rgb(DyeColor.RED),
                "the client copy reads " + copy.dye() + " / " + Integer.toHexString(copy.clothTint()));
        useItem(h, player, head, red);
        h.assertTrue(red.getCount() == 2, "the same dye again was spent");
        useItem(h, player, head.below(3), red);
        h.assertTrue(red.getCount() == 2 && yardBe(h, head.below(3)).dye() == null, "the lower yard took the dye");
        ItemStack white = new ItemStack(Items.WHITE_DYE);
        useItem(h, player, head, white);
        h.assertTrue(white.isEmpty() && clientCopy(h, yardBe(h, head)).clothTint() == SailTint.NONE,
                "white did not bring back the natural canvas");
        h.succeed();
    }

    /** A dye on the head cleat of a triangular sail dyes it, the client copy reads it; a banner there is refused and kept. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 40)
    public static void dyeColoursTheStaySailButNoBanner(GameTestHelper h) {
        SailingGameTestsShips.basin(h, false);
        SailingGameTestsShips.foreAndAftHull(h, 17, 17, SailTrim.FULL);
        BlockPos head = h.absolutePos(new BlockPos(19, 14, 20));
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getLevel().getBlockEntity(head) instanceof CleatBlockEntity c && c.cloth() != null, "the head cleat has no cloth");
            CleatBlockEntity be = (CleatBlockEntity) h.getLevel().getBlockEntity(head);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            ItemStack green = new ItemStack(Items.GREEN_DYE);
            h.assertTrue(useItem(h, player, head, green).consumesAction() && green.isEmpty(), "the stay sail was not dyed");
            h.assertTrue(be.dye() == DyeColor.GREEN, "head cleat dye " + be.dye());
            h.assertTrue(cleatCopy(h, be).clothTint() == SailTint.rgb(DyeColor.GREEN), "the client copy has no green tint");
            ItemStack banner = crossBanner(h);
            useItem(h, player, head, banner);
            h.assertTrue(banner.getCount() == 1, "a stay sail took a banner");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ banners

    /**
     * A banner on a 5 × 4 sail is spent and kept on the head; the client copy shows its base colour and both layers;
     * a dye is refused while it hangs; sneaking with an empty hand takes the same banner back.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40)
    public static void bannerHangsReachesTheClientAndComesBack(GameTestHelper h) {
        BlockPos head = landSail(h, 2, 2, 4);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack banner = crossBanner(h);
        ItemStack original = banner.copy();
        h.assertTrue(useItem(h, player, head.west(2), banner).consumesAction() && banner.isEmpty(), "the banner was not hung");
        YardBlockEntity be = yardBe(h, head);
        h.assertTrue(ItemStack.matches(be.banner(), original), "the head keeps " + be.banner());
        YardBlockEntity copy = clientCopy(h, be);
        h.assertTrue(copy.bannerShown(), "the client copy does not show the banner");
        h.assertTrue(copy.clothTint() == SailTint.rgb(DyeColor.BLUE), "cloth tint " + Integer.toHexString(copy.clothTint()));
        List<BannerPatternLayers.Layer> layers = copy.shownLayers();
        h.assertTrue(layers.size() == 2 && layers.get(0).pattern().is(BannerPatterns.CROSS) && layers.get(0).color() == DyeColor.RED
                && layers.get(1).pattern().is(BannerPatterns.BORDER) && layers.get(1).color() == DyeColor.YELLOW,
                "the client copy's layers: " + layers);
        ItemStack red = new ItemStack(Items.RED_DYE);
        useItem(h, player, head, red);
        h.assertTrue(red.getCount() == 1 && be.dye() == null, "a dye took while the banner hangs");
        ItemStack second = crossBanner(h);
        useItem(h, player, head, second);
        h.assertTrue(second.getCount() == 1, "a second banner was taken");
        InteractionResult back = useEmpty(h, player, head.east(), true);
        h.assertTrue(back.consumesAction(), "sneak-use did not take the banner back: " + back);
        h.assertTrue(be.banner().isEmpty() && !clientCopy(h, be).bannerShown(), "the banner is still on the sail");
        h.assertTrue(player.getInventory().items.stream().anyMatch(s -> ItemStack.matches(s, original)),
                "the player did not get the same banner back");
        h.succeed();
    }

    /** A sail whose shorter yard is under {@code banner_min_width} refuses a banner and keeps it in hand. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40)
    public static void tooSmallSailRefusesTheBanner(GameTestHelper h) {
        BlockPos head = landSail(h, 2, 0, 4); // upper 5, lower 1: the shorter yard counts
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack banner = crossBanner(h);
        useItem(h, player, head, banner);
        h.assertTrue(banner.getCount() == 1 && yardBe(h, head).banner().isEmpty(), "a too narrow sail took the banner");
        h.assertTrue(SailDecorations.hangBanner(h.getLevel(), head, banner) == SailDecorations.Outcome.TOO_SMALL, "not refused as too small");
        h.succeed();
    }

    /**
     * Lengthening the upper yard so its middle moves hands the banner to the new middle and the dye to the new block;
     * breaking the block that keeps the banner drops it.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40)
    public static void yardChangesCarryTheBannerAndBreakingDropsIt(GameTestHelper h) {
        BlockPos head = landSail(h, 2, 2, 4);
        SailDecorations.dyeYard(h.getLevel(), head, DyeColor.BLACK);
        // dyed, then the banner (a dye is refused while one hangs)
        h.assertTrue(SailDecorations.hangBanner(h.getLevel(), head, crossBanner(h)) == SailDecorations.Outcome.HUNG, "not hung");
        BlockPos added = head.west(3);
        // both yards 6 long: their middles move one block west, and stay one above the other
        h.getLevel().setBlock(added.below(4), h.getLevel().getBlockState(head.below(4)), Block.UPDATE_ALL);
        h.getLevel().setBlock(added, h.getLevel().getBlockState(head), Block.UPDATE_ALL);
        BlockPos newHead = head.west();
        h.assertTrue(yardBe(h, newHead).geometry() != null, "the new middle heads no sail");
        h.assertTrue(!yardBe(h, newHead).banner().isEmpty() && yardBe(h, head).banner().isEmpty(), "the banner did not move to the new middle");
        h.assertTrue(yardBe(h, added).dye() == DyeColor.BLACK, "the new yard block did not take the dye");
        h.getLevel().destroyBlock(newHead, false);
        h.runAfterDelay(1, () -> {
            List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(newHead).inflate(2));
            h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.BLUE_BANNER)), "breaking the yard did not drop the banner");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ ships

    /**
     * A dyed square sail with a banner on the test hull keeps both through assembly, a trim change (reefing to half and
     * furling) and disassembly, and no banner is dropped on the way.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 100)
    public static void assemblyReefingAndDisassemblyKeepTheSailsLooks(GameTestHelper h) {
        SailingGameTestsShips.basin(h, true);
        BlockPos helm = SailingGameTestsShips.squareHull(h, 17, 17, SailTrim.FULL);
        BlockPos headWorld = h.absolutePos(new BlockPos(19, 12, 19));
        SailDecorations.dyeYard(h.getLevel(), headWorld, DyeColor.RED);
        h.assertTrue(SailDecorations.hangBanner(h.getLevel(), headWorld, crossBanner(h)) == SailDecorations.Outcome.HUNG,
                "the 3 x 3 test sail refused the banner");
        Fixture f = SailingGameTestsShips.assemble(h, helm);
        BlockPos head = f.runtime().sailPositions().get(0);
        YardBlockEntity be = yardBe(h, head);
        h.assertTrue(be.dye() == DyeColor.RED && !be.banner().isEmpty(), "assembly lost the looks: " + be.dye() + ", " + be.banner());
        h.assertTrue(clientCopy(h, be).bannerShown() && clientCopy(h, be).shownLayers().size() == 2, "the client copy lost the banner on the ship");
        h.assertTrue(YardSails.cycle(h.getLevel(), head) == SailTrim.FURLED, "the trim did not cycle to furled");
        h.assertTrue(YardSails.cycle(h.getLevel(), head) == SailTrim.HALF, "the trim did not cycle to half");
        be = yardBe(h, head);
        h.assertTrue(be.dye() == DyeColor.RED && !be.banner().isEmpty() && clientCopy(h, be).bannerShown(), "reefing lost the looks");
        BlockPos helmPlot = f.ship().plotBlocks().stream()
                .filter(p -> h.getLevel().getBlockState(p).is(AssemblyContent.HELM.get())).findFirst().orElseThrow();
        AssemblyResult r = ShipAssembler.disassemble(f.ship(), helmPlot, null);
        h.assertTrue(r.outcome() == AssemblyResult.Outcome.DISASSEMBLED, "expected DISASSEMBLED, got " + r);
        h.runAfterDelay(2, () -> {
            AABB area = new AABB(h.absolutePos(new BlockPos(10, 8, 10)).getCenter(), h.absolutePos(new BlockPos(30, 14, 30)).getCenter());
            YardBlockEntity found = null;
            for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(area.minX, area.minY, area.minZ), BlockPos.containing(area.maxX, area.maxY, area.maxZ))) {
                if (h.getLevel().getBlockEntity(p) instanceof YardBlockEntity y && !y.banner().isEmpty()) {
                    found = y;
                    break;
                }
            }
            h.assertTrue(found != null && found.dye() == DyeColor.RED, "disassembly lost the banner or the dye");
            h.assertTrue(found.getBlockState().getValue(YardBlock.TRIM) == SailTrim.HALF, "the trim changed");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, area.inflate(4)).stream()
                    .noneMatch(e -> e.getItem().is(Items.BLUE_BANNER)), "a banner was dropped on the way");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ toggles

    /** {@code sails.dyeing} off: a dye does nothing on a sail (it goes on to the trim) and a dyed sail shows the canvas. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40, batch = "pirates_n_ships_config_sailing_dyeing")
    public static void dyeingOffLeavesSailsUndyed(GameTestHelper h) {
        BlockPos head = landSail(h, 1, 1, 3);
        SailDecorations.dyeYard(h.getLevel(), head, DyeColor.BLUE);
        ConfigOverrides.during(h, SailingConfig.SAIL_DYEING, false);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack red = new ItemStack(Items.RED_DYE);
        ItemInteractionResult r = useItem(h, player, head, red);
        h.assertTrue(r == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && red.getCount() == 1, "the dye was used: " + r);
        h.assertTrue(yardBe(h, head).dye() == DyeColor.BLUE, "the stored dye changed");
        h.assertTrue(clientCopy(h, yardBe(h, head)).clothTint() == SailTint.NONE, "a dyed sail is still tinted with dyeing off");
        h.succeed();
    }

    /** {@code sails.banners} off: a banner is not taken, a hung one is not shown, and it can still be taken back. */
    @ModGameTest(template = GameTestTemplates.EMPTY_24, timeoutTicks = 40, batch = "pirates_n_ships_config_sailing_banners")
    public static void bannersOffHidesButReturnsBanners(GameTestHelper h) {
        BlockPos head = landSail(h, 2, 2, 4);
        SailDecorations.hangBanner(h.getLevel(), head, crossBanner(h));
        ConfigOverrides.during(h, SailingConfig.SAIL_BANNERS, false);
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ItemStack banner = crossBanner(h);
        ItemInteractionResult r = useItem(h, player, head.east(), banner);
        h.assertTrue(r == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && banner.getCount() == 1, "the banner was used: " + r);
        YardBlockEntity copy = clientCopy(h, yardBe(h, head));
        h.assertTrue(!copy.bannerShown() && copy.shownLayers().isEmpty() && copy.clothTint() == SailTint.NONE,
                "the banner is still shown with banners off");
        h.assertTrue(useEmpty(h, player, head, true).consumesAction() && yardBe(h, head).banner().isEmpty(), "the banner could not be taken back");
        h.succeed();
    }
}
