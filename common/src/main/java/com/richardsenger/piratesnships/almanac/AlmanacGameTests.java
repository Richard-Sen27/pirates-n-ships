package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.core.block.Waterlogging;
import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Captain's Almanac's book and placed tome (ALM1, docs/design.md §3.4) with a server player whose payloads are
 * recorded: placement on top of a block only, waterlogging, the drop, reading the tome (it opens, sends the open
 * payload, stays open while read, closes after the close message or when the reader leaves), reading the book in hand,
 * the recipe, and {@code almanac.enabled} off.
 */
public final class AlmanacGameTests {

    private static final BlockPos TOME = new BlockPos(1, 1, 1);

    private AlmanacGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AlmanacGameTests.class);
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private static ServerPlayer player(GameTestHelper helper, String name) {
        // Not added to the level: a mock connection would receive (and reject) other mods' login payloads
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), name);
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(), connection, p, cookie);
        p.setPos(Vec3.atCenterOf(helper.absolutePos(TOME.north())));
        p.getInventory().clearContent();
        return p;
    }

    /** A tome lying on stone at {@link #TOME}; returns its absolute position. */
    private static BlockPos tome(GameTestHelper helper) {
        helper.setBlock(TOME.below(), Blocks.STONE);
        helper.setBlock(TOME, AlmanacContent.ALMANAC.get().defaultBlockState());
        return helper.absolutePos(TOME);
    }

    private static AlmanacBlockEntity entity(GameTestHelper helper) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(TOME)) instanceof AlmanacBlockEntity be) return be;
        throw new net.minecraft.gametest.framework.GameTestAssertException("no almanac block entity at " + TOME);
    }

    private static InteractionResult use(GameTestHelper helper, ServerPlayer p) {
        BlockPos abs = helper.absolutePos(TOME);
        return helper.getBlockState(TOME).useWithoutItem(helper.getLevel(), p,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    /** Uses the almanac item in {@code p}'s main hand on the {@code face} of the block at relative {@code on}. */
    private static InteractionResult useItemOn(GameTestHelper helper, ServerPlayer p, BlockPos on, Direction face) {
        BlockPos abs = helper.absolutePos(on);
        Vec3 hit = Vec3.atCenterOf(abs).add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5));
        return p.getMainHandItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, abs, false)));
    }

    private static ServerPlayer holding(GameTestHelper helper, String name, boolean sneaking) {
        ServerPlayer p = player(helper, name);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AlmanacContent.ALMANAC_ITEM.get()));
        p.setShiftKeyDown(sneaking);
        return p;
    }

    private static List<AlmanacPayloads.Open> opens(List<CustomPacketPayload> sent) {
        synchronized (sent) {
            return sent.stream().filter(AlmanacPayloads.Open.class::isInstance).map(AlmanacPayloads.Open.class::cast).toList();
        }
    }

    // --- placement ----------------------------------------------------------------------------------------------

    /** Sneak-use on top of a block sets the tome down there, facing the player; the item stacks to 1. */
    @ModGameTest
    public static void sneakUseOnTopOfABlockPlacesTheTome(GameTestHelper helper) {
        helper.setBlock(TOME.below(), Blocks.OAK_PLANKS);
        helper.setBlock(TOME, Blocks.AIR);
        ServerPlayer p = holding(helper, "alm_placer", true);
        helper.assertValueEqual(p.getMainHandItem().getMaxStackSize(), 1, "the almanac stacks to 1");
        helper.assertTrue(useItemOn(helper, p, TOME.below(), Direction.UP).consumesAction(), "placed");
        BlockState placed = helper.getBlockState(TOME);
        helper.assertTrue(placed.is(AlmanacContent.ALMANAC.get()), "the tome lies on the planks");
        helper.assertFalse(Waterlogging.isWaterlogged(placed), "dry in air");
        helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(TOME)) instanceof AlmanacBlockEntity, "with its block entity");
        helper.succeed();
    }

    /** No wall mounting: a sneak-use on the side of a block with nothing below the spot places nothing. */
    @ModGameTest
    public static void theTomeNeedsABlockBelow(GameTestHelper helper) {
        BlockPos wall = new BlockPos(1, 2, 1);
        BlockPos spot = wall.west();
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(spot, Blocks.AIR);
        helper.setBlock(spot.below(), Blocks.AIR);
        ServerPlayer p = holding(helper, "alm_wall", true);
        useItemOn(helper, p, wall, Direction.WEST);
        helper.assertBlockNotPresent(AlmanacContent.ALMANAC.get(), spot);
        BlockState state = AlmanacContent.ALMANAC.get().defaultBlockState();
        helper.assertFalse(state.canSurvive(helper.getLevel(), helper.absolutePos(spot)), "nothing below: cannot lie there");
        helper.succeed();
    }

    /** Placed in a water source the tome is waterlogged; it drops itself; it falls when its support goes. */
    @ModGameTest
    public static void tomeIsWaterloggedDropsItselfAndNeedsItsSupport(GameTestHelper helper) {
        helper.setBlock(TOME.below(), Blocks.STONE);
        helper.setBlock(TOME, Blocks.WATER);
        ServerPlayer p = holding(helper, "alm_water", true);
        useItemOn(helper, p, TOME.below(), Direction.UP);
        BlockState placed = helper.getBlockState(TOME);
        helper.assertTrue(placed.is(AlmanacContent.ALMANAC.get()), "placed in water");
        helper.assertTrue(Waterlogging.isWaterlogged(placed), "waterlogged");

        BlockPos abs = helper.absolutePos(TOME);
        List<ItemStack> drops = Block.getDrops(placed, helper.getLevel(), abs, helper.getLevel().getBlockEntity(abs));
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(AlmanacContent.ALMANAC_ITEM.get()), "drops itself: " + drops);

        helper.setBlock(TOME.below(), Blocks.AIR);
        helper.assertBlockNotPresent(AlmanacContent.ALMANAC.get(), TOME);
        helper.assertTrue(helper.getLevel().getFluidState(abs).isSource(), "its water stays behind");
        helper.succeed();
    }

    // --- reading the tome ---------------------------------------------------------------------------------------

    /** Right-click opens the tome at once, sends the open payload with its position, and the tome stays open. */
    @ModGameTest
    public static void rightClickOpensTheTomeAndSendsTheOpenPayload(GameTestHelper helper) {
        BlockPos abs = tome(helper);
        ServerPlayer p = player(helper, "alm_reader");
        List<CustomPacketPayload> sent = AlmanacReading.record(p.getUUID());
        AlmanacBlockEntity be = entity(helper);
        helper.assertFalse(be.isRead(), "closed before");
        helper.assertTrue(use(helper, p).consumesAction(), "the tome is read");
        List<AlmanacPayloads.Open> opens = opens(sent);
        helper.assertValueEqual(opens.size(), 1, "one open payload");
        helper.assertValueEqual(opens.get(0).tome(), Optional.of(abs), "for this tome");
        helper.assertTrue(be.isRead(), "it lies open");
        helper.assertTrue(be.hasReader(p.getUUID()), "the reader has it open");
        // the reader closes it: it stays open for open_ticks (100) all the same
        AlmanacReading.close(p, new AlmanacPayloads.Close(Optional.of(abs)));
        helper.assertFalse(be.hasReader(p.getUUID()), "the reader is gone");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(be.isRead(), "still open within open_ticks");
            AlmanacReading.stopRecording(p.getUUID());
            helper.succeed();
        });
    }

    /** After the close message the tome closes once open_ticks have passed since the right-click. */
    @ModGameTest(batch = "pirates_n_ships_config_almanac_open_ticks_close", timeoutTicks = 100)
    public static void theCloseMessageClosesTheTomeAfterOpenTicks(GameTestHelper helper) {
        ConfigOverrides.during(helper, AlmanacConfig.OPEN_TICKS, 10);
        BlockPos abs = tome(helper);
        ServerPlayer p = player(helper, "alm_closer");
        AlmanacReading.record(p.getUUID());
        use(helper, p);
        long openedAt = helper.getLevel().getGameTime();
        AlmanacBlockEntity be = entity(helper);
        AlmanacReading.close(p, new AlmanacPayloads.Close(Optional.of(abs)));
        helper.succeedWhen(() -> {
            helper.assertFalse(be.isRead(), "closed");
            helper.assertTrue(helper.getLevel().getGameTime() - openedAt >= 10, "not before open_ticks");
            AlmanacReading.stopRecording(p.getUUID());
        });
    }

    /** A reader who keeps it open keeps it open past open_ticks; walking away closes it. */
    @ModGameTest(batch = "pirates_n_ships_config_almanac_open_ticks_leave", timeoutTicks = 120)
    public static void theTomeStaysOpenWhileReadAndClosesWhenTheReaderLeaves(GameTestHelper helper) {
        ConfigOverrides.during(helper, AlmanacConfig.OPEN_TICKS, 10);
        tome(helper);
        ServerPlayer p = player(helper, "alm_stayer");
        AlmanacReading.record(p.getUUID());
        use(helper, p);
        AlmanacBlockEntity be = entity(helper);
        boolean[] left = {false};
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(be.isRead(), "still open while read, past open_ticks");
            p.setPos(Vec3.atCenterOf(helper.absolutePos(TOME)).add(30, 0, 0));
            left[0] = true;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left[0], "the reader has left");
            helper.assertFalse(be.isRead(), "closed once the reader left");
            helper.assertFalse(be.hasReader(p.getUUID()), "no reader any more");
            AlmanacReading.stopRecording(p.getUUID());
        });
    }

    // --- reading in hand ----------------------------------------------------------------------------------------

    /** Using the book in hand, in the air or at a block, sends the open payload without a position and places nothing. */
    @ModGameTest
    public static void theBookInHandSendsTheOpenPayload(GameTestHelper helper) {
        helper.setBlock(TOME.below(), Blocks.STONE);
        helper.setBlock(TOME, Blocks.AIR);
        ServerPlayer p = holding(helper, "alm_hand", false);
        List<CustomPacketPayload> sent = AlmanacReading.record(p.getUUID());
        helper.assertTrue(p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult().consumesAction(), "read in hand");
        helper.assertTrue(useItemOn(helper, p, TOME.below(), Direction.UP).consumesAction(), "read while looking at a block");
        List<AlmanacPayloads.Open> opens = opens(sent);
        helper.assertValueEqual(opens.size(), 2, "two open payloads");
        helper.assertTrue(opens.stream().allMatch(o -> o.tome().isEmpty()), "without a tome position");
        helper.assertBlockNotPresent(AlmanacContent.ALMANAC.get(), TOME);
        helper.assertValueEqual(p.getMainHandItem().getCount(), 1, "the book stays in hand");
        AlmanacReading.stopRecording(p.getUUID());
        helper.succeed();
    }

    // --- recipe and config --------------------------------------------------------------------------------------

    /** One almanac from a book, two leather and two copper ingots. */
    @ModGameTest
    public static void almanacRecipe(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        List<RecipeHolder<?>> recipes = helper.getLevel().getRecipeManager().getRecipes().stream()
                .filter(r -> r.value().getResultItem(registries).is(AlmanacContent.ALMANAC_ITEM.get())).toList();
        helper.assertValueEqual(recipes.size(), 1, "one almanac recipe");
        ItemStack result = recipes.get(0).value().getResultItem(registries);
        helper.assertValueEqual(result.getCount(), 1, "makes one");
        List<Ingredient> ingredients = recipes.get(0).value().getIngredients();
        helper.assertValueEqual(ingredients.size(), 5, "five ingredients");
        helper.assertValueEqual(count(ingredients, Items.BOOK), 1L, "a book");
        helper.assertValueEqual(count(ingredients, Items.LEATHER), 2L, "two leather");
        helper.assertValueEqual(count(ingredients, Items.COPPER_INGOT), 2L, "two copper ingots");
        helper.succeed();
    }

    private static long count(List<Ingredient> ingredients, Item item) {
        return ingredients.stream().filter(i -> i.test(new ItemStack(item))).count();
    }

    /** With almanac.enabled off neither the tome nor the book is read, and the tome never opens. */
    @ModGameTest(batch = "pirates_n_ships_config_almanac_enabled")
    public static void switchedOffNothingIsRead(GameTestHelper helper) {
        ConfigOverrides.during(helper, AlmanacConfig.ENABLED, false);
        BlockPos abs = tome(helper);
        ServerPlayer p = holding(helper, "alm_off", false);
        List<CustomPacketPayload> sent = AlmanacReading.record(p.getUUID());
        helper.assertValueEqual(use(helper, p), InteractionResult.PASS, "the tome is decoration");
        helper.assertFalse(AlmanacReading.readTome(p, abs), "reading the tome is refused");
        helper.assertValueEqual(p.getMainHandItem().use(helper.getLevel(), p, InteractionHand.MAIN_HAND).getResult(),
                InteractionResult.PASS, "the book is not read in hand");
        helper.assertTrue(sent.isEmpty(), "nothing sent");
        AlmanacBlockEntity be = entity(helper);
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(be.isRead(), "never opens");
            AlmanacReading.stopRecording(p.getUUID());
            helper.succeed();
        });
    }
}
