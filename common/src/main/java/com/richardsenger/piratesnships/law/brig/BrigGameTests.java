package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.content.BrigDoorBlock;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.UUID;

/** Prisoners, leading, the brig door lock, cells, escapes and outcomes on real entities and blocks. */
public final class BrigGameTests {

    private BrigGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(BrigGameTests.class);
    }

    // --- Helpers ------------------------------------------------------------------------------------------------

    private static void floor(GameTestHelper helper, int size) {
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static Mob weakPillager(GameTestHelper helper, BlockPos pos) {
        Mob mob = helper.spawnWithNoFreeWill(EntityType.PILLAGER, pos);
        mob.setHealth(4.0f);
        return mob;
    }

    private static Mob captured(GameTestHelper helper, Player captor, BlockPos pos) {
        Mob mob = weakPillager(helper, pos);
        helper.assertValueEqual(BrigService.capture(captor, mob), CaptureRules.Result.OK, "capture result");
        return mob;
    }

    private static BlockPos placeDoor(GameTestHelper helper, BlockPos lower, Direction facing) {
        BlockState base = LawContent.BRIG_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, facing);
        helper.setBlock(lower, base.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(lower.above(), base.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        return lower;
    }

    // --- Capture ------------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void shacklesRefusedOnHealthyMobWorkOnWeakened(GameTestHelper helper) {
        floor(helper, 9);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(LawContent.SHACKLES.get(), 2);
        Mob healthy = helper.spawnWithNoFreeWill(EntityType.PILLAGER, new BlockPos(2, 1, 2));
        InteractionResult refused = stack.interactLivingEntity(player, healthy, InteractionHand.MAIN_HAND);
        helper.assertFalse(refused.consumesAction(), "healthy mob should refuse, got " + refused);
        helper.assertFalse(BrigService.isPrisoner(healthy), "healthy mob is no prisoner");
        helper.assertValueEqual(stack.getCount(), 2, "no shackles used on refusal");

        Mob weak = weakPillager(helper, new BlockPos(5, 1, 5));
        InteractionResult ok = stack.interactLivingEntity(player, weak, InteractionHand.MAIN_HAND);
        helper.assertTrue(ok.consumesAction(), "weakened mob should be shackled, got " + ok);
        PrisonerState st = BrigService.state(weak);
        helper.assertTrue(st.active() && st.heldBy(player.getUUID()), "prisoner of the player: " + st);
        helper.assertTrue(st.led(), "a fresh prisoner is led");
        helper.assertValueEqual(stack.getCount(), 1, "one pair of shackles used");
        helper.assertTrue(weak.isPersistenceRequired(), "a prisoner never despawns");
        helper.assertTrue(EntityType.WITHER.is(BrigService.NOT_CAPTURABLE), "the wither is in the not_capturable tag");
        healthy.discard();
        weak.discard();
        helper.succeed();
    }

    private static Villager tradingVillager(GameTestHelper helper, BlockPos pos) {
        Villager v = helper.spawnWithNoFreeWill(EntityType.VILLAGER, pos);
        v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER));
        return v;
    }

    /** Fires the interaction event the way the loader forwarder does, with shackles in the main hand. */
    private static InteractionResult clickWithShackles(Player player, ItemStack shackles, LivingEntity target) {
        player.setItemInHand(InteractionHand.MAIN_HAND, shackles);
        return CommonEvents.ENTITY_INTERACT.invoker().onInteract(player, target, InteractionHand.MAIN_HAND);
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void weakenedVillagerAndTraderAreShackledThroughTheInteractEvent(GameTestHelper helper) {
        floor(helper, 9);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(LawContent.SHACKLES.get(), 3);
        Villager villager = tradingVillager(helper, new BlockPos(2, 1, 2));
        villager.setHealth(2.0f);
        InteractionResult r = clickWithShackles(player, stack, villager);
        helper.assertTrue(r.consumesAction(), "weakened villager should be shackled, got " + r);
        helper.assertTrue(BrigService.state(villager).heldBy(player.getUUID()), "villager is the player's prisoner");
        helper.assertFalse(villager.isTrading(), "no trade screen for the captor");
        helper.assertValueEqual(stack.getCount(), 2, "one pair of shackles used");

        InteractionResult again = clickWithShackles(player, stack, villager);
        helper.assertTrue(again.consumesAction(), "own prisoner: the brig handles the click, got " + again);
        helper.assertFalse(BrigService.state(villager).led(), "second click lets go of the chain");
        helper.assertFalse(villager.isTrading(), "no trade screen on the own prisoner");

        WanderingTrader trader = helper.spawnWithNoFreeWill(EntityType.WANDERING_TRADER, new BlockPos(5, 1, 5));
        trader.setHealth(2.0f);
        InteractionResult t = clickWithShackles(player, stack, trader);
        helper.assertTrue(t.consumesAction(), "weakened wandering trader should be shackled, got " + t);
        helper.assertTrue(BrigService.isPrisoner(trader), "trader is a prisoner");
        helper.assertValueEqual(stack.getCount(), 1, "second pair of shackles used");

        helper.assertValueEqual(clickWithShackles(player, ItemStack.EMPTY, tradingVillager(helper, new BlockPos(6, 1, 2))),
                InteractionResult.PASS, "an empty hand leaves villagers to vanilla");
        BrigService.clear(villager);
        BrigService.clear(trader);
        helper.killAllEntities();
        helper.succeed();
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void healthyVillagerRefusesShacklesWithoutOpeningTrades(GameTestHelper helper) {
        floor(helper, 9);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(LawContent.SHACKLES.get(), 2);
        Villager villager = tradingVillager(helper, new BlockPos(2, 1, 2));
        InteractionResult r = clickWithShackles(player, stack, villager);
        helper.assertTrue(r != InteractionResult.PASS, "the brig claims the click, so vanilla's trades don't run");
        helper.assertFalse(r.consumesAction(), "a healthy villager refuses, got " + r);
        helper.assertFalse(BrigService.isPrisoner(villager), "healthy villager is no prisoner");
        helper.assertValueEqual(stack.getCount(), 2, "no shackles used on refusal");
        helper.assertFalse(villager.isTrading(), "no trade screen");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "no menu opened");
        helper.killAllEntities();
        helper.succeed();
    }

    @ModGameTest
    public static void prisonerStateSurvivesNbtRoundTrip(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob mob = captured(helper, player, new BlockPos(1, 1, 1));
        PrisonerState before = BrigService.state(mob);
        CompoundTag tag = mob.saveWithoutId(new CompoundTag());
        Mob copy = EntityType.PILLAGER.create(helper.getLevel());
        helper.assertTrue(copy != null, "pillager created");
        copy.load(tag);
        helper.assertValueEqual(Services.ATTACHMENTS.get(copy, BrigService.PRISONER), before, "prisoner state after NBT round trip");
        copy.discard();
        mob.discard();
        helper.succeed();
    }

    @ModGameTest
    public static void prisonersDealNoDamage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob mob = captured(helper, player, new BlockPos(1, 1, 1));
        float amount = BrigService.onDamage(player, helper.getLevel().damageSources().mobAttack(mob), 5.0f);
        helper.assertValueEqual(amount, 0.0f, "damage from a prisoner");
        BrigService.clear(mob);
        helper.assertValueEqual(BrigService.onDamage(player, helper.getLevel().damageSources().mobAttack(mob), 5.0f), 5.0f, "damage after release");
        mob.discard();
        helper.succeed();
    }

    // --- Leading ------------------------------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9, timeoutTicks = 200)
    public static void prisonerFollowsCaptor(GameTestHelper helper) {
        floor(helper, 9);
        // Captured by a mock player, then the chain is handed to a stand-in captor in the level (a mob without AI):
        // mock server players can't join a level with Sable (its login payloads can't be sent to a fake connection).
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob captor = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(7, 1, 7));
        // A mob with AI (navigation runs); restrained from the moment of capture
        Mob mob = helper.spawn(EntityType.PILLAGER, new BlockPos(1, 1, 1));
        mob.setHealth(4.0f);
        helper.assertValueEqual(BrigService.capture(player, mob), CaptureRules.Result.OK, "capture result");
        Services.ATTACHMENTS.set(mob, BrigService.PRISONER, BrigService.state(mob).withCaptor(captor.getUUID(), "Captor"));
        helper.assertTrue(mob.distanceTo(captor) > 6.0f, "starts far away");
        helper.succeedWhen(() -> {
            helper.assertTrue(BrigService.state(mob).led(), "still led");
            helper.assertTrue(mob.distanceTo(captor) < 4.0f, "prisoner should have followed, distance " + mob.distanceTo(captor));
            helper.assertTrue(mob.getTarget() == null, "a prisoner has no target");
            mob.discard();
            captor.discard();
        });
    }

    // --- Brig door ----------------------------------------------------------------------------------------------

    /**
     * The brig key locks and unlocks (P1): sneak-use without a key changes nothing, a key in anyone's hand locks and
     * unlocks both halves without changing the owner, the owner still opens the locked door without a key, others,
     * mobs and redstone don't.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void lockedDoorRefusesOthersMobsAndRedstone(GameTestHelper helper) {
        floor(helper, 9);
        BlockPos lower = placeDoor(helper, new BlockPos(4, 1, 4), Direction.NORTH);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        BrigDoorBlock.setOwner(helper.getLevel(), helper.absolutePos(lower), owner);
        // Sneak-use without a key no longer locks, not even for the owner
        owner.setShiftKeyDown(true);
        helper.useBlock(lower, owner);
        owner.setShiftKeyDown(false);
        helper.assertFalse(helper.getBlockState(lower).getValue(BrigDoorBlock.LOCKED), "sneak-use without a key must not lock");
        helper.assertFalse(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "sneak-use without a key must not open");
        // Another player with a key locks it (on the upper half); the owner stays
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LawContent.BRIG_KEY.get()));
        helper.useBlock(lower.above(), other);
        helper.assertTrue(helper.getBlockState(lower).getValue(BrigDoorBlock.LOCKED), "locked with the key");
        helper.assertTrue(helper.getBlockState(lower.above()).getValue(BrigDoorBlock.LOCKED), "upper half locked too");
        helper.assertFalse(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "the key does not open the door");
        helper.assertValueEqual(BrigDoorBlock.owner(helper.getLevel(), helper.absolutePos(lower)), owner.getUUID(), "owner unchanged");
        // Without the key that player can neither open nor unlock it
        other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(lower, other);
        helper.assertFalse(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "other player can't open");
        other.setShiftKeyDown(true);
        helper.useBlock(lower, other);
        other.setShiftKeyDown(false);
        helper.assertTrue(helper.getBlockState(lower).getValue(BrigDoorBlock.LOCKED), "other player can't unlock without a key");
        // A mob (villager door behavior) and redstone can't open it
        Mob mob = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(2, 1, 2));
        BlockPos abs = helper.absolutePos(lower);
        ((DoorBlock) LawContent.BRIG_DOOR.get()).setOpen(mob, helper.getLevel(), helper.getLevel().getBlockState(abs), abs, true);
        helper.assertFalse(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "mob can't open");
        helper.setBlock(lower.east(), Blocks.REDSTONE_BLOCK);
        helper.assertFalse(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "redstone can't open");
        helper.setBlock(lower.east(), Blocks.AIR);
        // The owner opens it without a key, and it stays locked
        helper.useBlock(lower, owner);
        helper.assertTrue(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "owner opens the locked door");
        helper.assertTrue(helper.getBlockState(lower.above()).getValue(DoorBlock.OPEN), "upper half opens too");
        helper.assertTrue(helper.getBlockState(lower).getValue(BrigDoorBlock.LOCKED), "still locked");
        helper.useBlock(lower, owner);
        helper.assertFalse(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "owner closes it again");
        // The owner can't unlock it by sneak-use any more, but with a key (sneaking: the item's own use)
        owner.setShiftKeyDown(true);
        helper.useBlock(lower, owner);
        helper.assertTrue(helper.getBlockState(lower).getValue(BrigDoorBlock.LOCKED), "sneak-use without a key must not unlock");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LawContent.BRIG_KEY.get()));
        BlockPos absLower = helper.absolutePos(lower);
        LawContent.BRIG_KEY.get().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absLower), Direction.NORTH, absLower, false)));
        owner.setShiftKeyDown(false);
        helper.assertFalse(helper.getBlockState(lower).getValue(BrigDoorBlock.LOCKED), "unlocked with the key");
        helper.assertFalse(helper.getBlockState(lower.above()).getValue(BrigDoorBlock.LOCKED), "upper half unlocked too");
        // Unlocked, anyone opens it
        helper.useBlock(lower, other);
        helper.assertTrue(helper.getBlockState(lower).getValue(DoorBlock.OPEN), "unlocked door opens for anyone");
        mob.discard();
        helper.succeed();
    }

    // --- Cells --------------------------------------------------------------------------------------------------

    /** A 1×2×1 cell at (4,1..2,4): bars on three sides, a brig door to the south, stone floor and ceiling. */
    @ModGameTest(template = GameTestTemplates.EMPTY_9)
    public static void prisonerInLockedCellIsHeld(GameTestHelper helper) {
        floor(helper, 9);
        BlockPos cell = new BlockPos(4, 1, 4);
        for (int y = 1; y <= 2; y++) {
            helper.setBlock(new BlockPos(3, y, 4), LawContent.BRIG_BARS.get());
            helper.setBlock(new BlockPos(5, y, 4), LawContent.BRIG_BARS.get());
            helper.setBlock(new BlockPos(4, y, 3), LawContent.BRIG_BARS.get());
        }
        helper.setBlock(new BlockPos(4, 3, 4), Blocks.STONE);
        BlockPos door = placeDoor(helper, new BlockPos(4, 1, 5), Direction.NORTH);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob mob = captured(helper, player, cell);
        BlockPos absDoor = helper.absolutePos(door);
        helper.assertFalse(BrigService.isInCell(mob), "unlocked door: no cell");
        BrigDoorBlock.setLocked(helper.getLevel(), absDoor, true);
        helper.assertTrue(BrigService.isInCell(mob), "locked door: in a cell");
        // Tight around the cell: neighboring tests have prisoners of their own
        AABB area = new AABB(helper.absolutePos(cell)).inflate(1.0);
        helper.assertValueEqual(BrigService.countPrisoners(helper.getLevel(), area, true), 1, "held prisoners in the area");
        BrigDoorBlock.setLocked(helper.getLevel(), absDoor, false);
        helper.assertFalse(BrigService.isInCell(mob), "unlocked again: no cell");
        helper.assertValueEqual(BrigService.countPrisoners(helper.getLevel(), area, true), 0, "no held prisoners");
        helper.assertValueEqual(BrigService.countPrisoners(helper.getLevel(), area, false), 1, "still one prisoner in the area");
        mob.discard();
        helper.succeed();
    }

    // --- Escapes (config: own batches) --------------------------------------------------------------------------

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_brig_escape", timeoutTicks = 60)
    public static void prisonerEscapesWithChanceOne(GameTestHelper helper) {
        floor(helper, 9);
        ConfigOverrides.during(helper, LawConfig.PRISONER_ESCAPES, true);
        ConfigOverrides.during(helper, BrigConfig.ESCAPE_CHANCE_PER_MINUTE, 1.0);
        ConfigOverrides.during(helper, BrigConfig.CHECK_INTERVAL_TICKS, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob mob = captured(helper, player, new BlockPos(4, 1, 4));
        BrigService.setLed(mob, false);
        helper.succeedWhen(() -> {
            helper.assertFalse(BrigService.isPrisoner(mob), "prisoner should have escaped");
            mob.discard();
        });
    }

    @ModGameTest(template = GameTestTemplates.EMPTY_9, batch = "pirates_n_ships_config_brig_no_escape")
    public static void noEscapeWithToggleOff(GameTestHelper helper) {
        floor(helper, 9);
        ConfigOverrides.during(helper, LawConfig.PRISONER_ESCAPES, false);
        ConfigOverrides.during(helper, BrigConfig.ESCAPE_CHANCE_PER_MINUTE, 1.0);
        ConfigOverrides.during(helper, BrigConfig.CHECK_INTERVAL_TICKS, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob mob = captured(helper, player, new BlockPos(4, 1, 4));
        BrigService.setLed(mob, false);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(BrigService.isPrisoner(mob), "no escape with prisoner_escapes off");
            mob.discard();
            helper.succeed();
        });
    }

    // --- Outcomes -----------------------------------------------------------------------------------------------

    @ModGameTest
    public static void deliverPaysBountyAndRemovesPrisoner(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob mob = captured(helper, player, new BlockPos(1, 1, 1));
        var placed = LawService.placeBounty(server, UUID.randomUUID(), "Tester", BountyTarget.npc(mob.getUUID(), "Pillager"), 40);
        helper.assertTrue(placed.placed(), "bounty placed: " + placed.outcome());
        PrisonerOutcomes.DeliverResult r = PrisonerOutcomes.deliver(mob, player, null);
        helper.assertTrue(r.success(), "delivered: " + r.failure());
        helper.assertTrue(r.payout() >= 40, "payout at least the bounty, got " + r.payout());
        helper.assertTrue(mob.isRemoved(), "delivered prisoner is handed over");
        helper.assertFalse(LawService.hasBounty(server, mob.getUUID()), "bounty claimed");

        Mob clean = captured(helper, player, new BlockPos(1, 1, 1));
        PrisonerOutcomes.DeliverResult none = PrisonerOutcomes.deliver(clean, player, null);
        helper.assertValueEqual(none.failure(), PrisonerOutcomes.Failure.NOTHING_TO_CLAIM, "no bounty, no tier");
        helper.assertTrue(BrigService.isPrisoner(clean), "refused delivery keeps the prisoner");
        clean.discard();
        helper.succeed();
    }

    @ModGameTest
    public static void releaseFreesAndDropsShackles(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob mob = captured(helper, player, new BlockPos(1, 1, 1));
        PrisonerOutcomes.ReleaseResult r = PrisonerOutcomes.release(mob);
        helper.assertTrue(r.success(), "released");
        helper.assertFalse(BrigService.isPrisoner(mob), "no longer a prisoner");
        helper.assertFalse(PrisonerOutcomes.release(mob).success(), "a second release is refused");
        helper.runAfterDelay(1, () -> {
            helper.assertItemEntityPresent(LawContent.SHACKLES.get(), new BlockPos(1, 1, 1), 2.0);
            mob.discard();
            helper.succeed();
        });
    }

    @ModGameTest
    public static void ransomAndPressGang(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Mob merchant = captured(helper, player, new BlockPos(1, 1, 1));
        PrisonerOutcomes.RansomResult ransom = PrisonerOutcomes.ransom(merchant, RansomRules.Kind.MERCHANT, false);
        helper.assertTrue(ransom.success(), "ransomed");
        helper.assertValueEqual(ransom.amount(), BrigConfig.RANSOM_MERCHANT.get(), "merchant ransom");
        helper.assertTrue(merchant.isRemoved(), "ransomed prisoner goes home");

        Mob sailor = captured(helper, player, new BlockPos(1, 1, 1));
        PrisonerOutcomes.PressGangResult pg = PrisonerOutcomes.pressGang(sailor, player);
        helper.assertTrue(pg.success() && pg.recruit() != null, "press-ganged");
        helper.assertValueEqual(pg.recruit().crime().outcome(), CriminalRecord.CrimeOutcome.COUNTED, "press-ganging is a crime");
        helper.assertTrue(LawService.record(player).score() > 0, "captain's criminal score rose");
        helper.assertTrue(sailor.isRemoved(), "the recruit leaves as an entity, the crew system spawns it");
        helper.succeed();
    }
}
