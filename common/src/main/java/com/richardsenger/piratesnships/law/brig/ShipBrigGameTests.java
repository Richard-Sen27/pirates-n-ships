package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.GameTestTemplates;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.crew.CrewConfig;
import com.richardsenger.piratesnships.crew.npc.CrewMember;
import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.content.BrigDoorBlock;
import com.richardsenger.piratesnships.law.content.LawContent;
import com.richardsenger.piratesnships.station.StationContent;
import com.richardsenger.piratesnships.station.StationGameTests;
import com.richardsenger.piratesnships.station.StationGameTests.Fixture;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * A brig cell aboard a ship and the crew's morale (FIX1, design.md §13.3): a prisoner locked in a cell on the deck of
 * the 5×4×5 test ship of {@link StationGameTests} (resting on land; helm at relative (19, 9, 18), the sail's yards
 * along x at z = 19). The cell is the 1×2×1 space at relative (20, 9..10, 17) on the bow edge: bars west, east and
 * north (overhanging the hull), a locked brig door south, planks above, the deck below. Two crew stand on the deck
 * without AI.
 * <p>
 * The out-of-cell escape chance is 0 and checks run every tick, so only the low-morale chance can free the prisoner;
 * each test changes config, so each runs in a batch of its own.
 */
public final class ShipBrigGameTests {

    private static final String CONFIG_BATCH = "pirates_n_ships_config_brig_";

    private ShipBrigGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(ShipBrigGameTests.class);
    }

    private record Brig(Fixture f, Mob prisoner, List<CrewMember> crew) { }

    /** The cell on the deck, placed before the ship is assembled (relative positions). */
    private static void cell(GameTestHelper h) {
        for (int y = 9; y <= 10; y++) {
            h.setBlock(new BlockPos(19, y, 17), LawContent.BRIG_BARS.get());
            h.setBlock(new BlockPos(21, y, 17), LawContent.BRIG_BARS.get());
            h.setBlock(new BlockPos(20, y, 16), LawContent.BRIG_BARS.get());
        }
        h.setBlock(new BlockPos(20, 11, 17), Blocks.OAK_PLANKS);
        BlockState door = LawContent.BRIG_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(BrigDoorBlock.LOCKED, true);
        h.setBlock(new BlockPos(20, 9, 18), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        h.setBlock(new BlockPos(20, 10, 18), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** A mob without AI standing at relative (x, 9, z) of the ship's deck. */
    private static <T extends Mob> T onDeck(GameTestHelper h, Fixture f, EntityType<T> type, int x, int z) {
        T mob = type.create(h.getLevel());
        if (mob == null) throw new AssertionError("could not create " + type);
        Vec3 p = f.ship().toWorld(Vec3.atBottomCenterOf(f.helm().offset(x - 19, 0, z - 18)));
        mob.moveTo(p.x, p.y, p.z, 0, 0);
        mob.setNoAi(true);
        h.getLevel().addFreshEntity(mob);
        return mob;
    }

    /**
     * The ship with the cell, two crew at {@code morale} and a shackled pillager in the cell. Escapes are on, the
     * chance outside a cell 0, checks every tick; the low-morale chance stays at its default until the test raises it.
     */
    private static Brig brig(GameTestHelper h, int morale) {
        ConfigOverrides.during(h, LawConfig.PRISONER_ESCAPES, true);
        ConfigOverrides.during(h, BrigConfig.ESCAPE_CHANCE_PER_MINUTE, 0.0);
        ConfigOverrides.during(h, BrigConfig.LOW_MORALE_ESCAPE_CHANCE_PER_MINUTE, 0.0);
        ConfigOverrides.during(h, BrigConfig.CHECK_INTERVAL_TICKS, 1);
        Fixture f = StationGameTests.ship(h, false, ShipBrigGameTests::cell);
        List<CrewMember> crew = List.of(onDeck(h, f, StationContent.CREW_MEMBER.get(), 20, 20),
                onDeck(h, f, StationContent.CREW_MEMBER.get(), 18, 19));
        for (CrewMember c : crew) c.setStoredMorale(morale);
        Mob prisoner = onDeck(h, f, EntityType.PILLAGER, 20, 17);
        prisoner.setHealth(4.0f);
        Player captor = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertValueEqual(BrigService.capture(captor, prisoner), CaptureRules.Result.OK, "capture result");
        BrigService.setLed(prisoner, false);
        return new Brig(f, prisoner, crew);
    }

    /**
     * Runs {@code body} once Sable has filled the ship's world bounds (they read empty for a tick or more after
     * assembly), so the crew and the prisoner are found aboard.
     */
    private static void whenReady(GameTestHelper h, Brig b, Runnable body) {
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            net.minecraft.world.phys.AABB box = b.f().ship().worldBounds();
            if (box.getXsize() > 1 && box.getZsize() > 1) body.run(); else h.runAfterDelay(1, poll[0]);
        };
        h.runAfterDelay(2, poll[0]);
    }

    private static void cleanup(Brig b) {
        b.prisoner().discard();
        b.crew().forEach(CrewMember::discard);
    }

    /**
     * Crew at morale 10 (below {@code desert_below} 20) and the low-morale chance at 100 % a minute: the prisoner is in
     * its cell aboard, the crew's morale reads low, and it escapes the locked cell within a few checks.
     */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = CONFIG_BATCH + "ship_low_morale")
    public static void lowMoraleShipLosesItsPrisoner(GameTestHelper h) {
        Brig b = brig(h, 10);
        whenReady(h, b, () -> {
            h.assertTrue(BrigService.isPrisoner(b.prisoner()), "the prisoner escaped before the chance was raised");
            h.assertTrue(BrigService.isInCell(b.prisoner()), "the prisoner is not in its cell aboard");
            h.assertTrue(BrigService.crewMoraleLow(b.prisoner()), "the crew's morale does not read low");
            ConfigOverrides.during(h, BrigConfig.LOW_MORALE_ESCAPE_CHANCE_PER_MINUTE, 1.0);
            h.succeedWhen(() -> {
                h.assertFalse(BrigService.isPrisoner(b.prisoner()), "the prisoner is still held");
                cleanup(b);
            });
        });
    }

    /** The same cell, crew at morale 70 and the chance at 100 %: the prisoner stays held for 40 checks. */
    @ModGameTest(template = GameTestTemplates.EMPTY_40, timeoutTicks = 120, batch = CONFIG_BATCH + "ship_high_morale")
    public static void contentCrewKeepsItsPrisoner(GameTestHelper h) {
        Brig b = brig(h, 70);
        whenReady(h, b, () -> {
            h.assertTrue(BrigService.isInCell(b.prisoner()), "the prisoner is not in its cell aboard");
            h.assertFalse(BrigService.crewMoraleLow(b.prisoner()), "a content crew reads low");
            ConfigOverrides.during(h, BrigConfig.LOW_MORALE_ESCAPE_CHANCE_PER_MINUTE, 1.0);
            h.runAfterDelay(40, () -> {
                h.assertTrue(BrigService.isPrisoner(b.prisoner()), "the prisoner escaped a content crew's brig");
                h.assertTrue(BrigService.isInCell(b.prisoner()), "the prisoner left its cell");
                cleanup(b);
                h.succeed();
            });
        });
    }
}
