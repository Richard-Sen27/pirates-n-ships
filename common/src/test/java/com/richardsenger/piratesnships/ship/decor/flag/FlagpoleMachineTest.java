package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine.Cause;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine.Delivery;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine.Feedback;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine.Input;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleMachine.Outcome;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState.Action;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every input in every state, cancelling, breaking and timing. Vanilla items stand in for our flag items. */
class FlagpoleMachineTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final int DELAY = 60;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack merchant() {
        return new ItemStack(Items.WHITE_WOOL);
    }

    private static ItemStack navy() {
        return new ItemStack(Items.BLUE_WOOL);
    }

    private static FlagpoleState flying(FlagKind kind, ItemStack item, boolean struck) {
        return new FlagpoleState(kind, item, struck, Optional.of(B), Optional.empty());
    }

    private static Outcome run(Outcome started) {
        return FlagpoleMachine.tick(started.state(), started.state().pending().orElseThrow().finishTick());
    }

    private static void assertDelivered(List<Delivery> deliveries, UUID to, ItemStack stack) {
        assertTrue(deliveries.stream().anyMatch(d -> java.util.Objects.equals(d.recipient(), to) && ItemStack.matches(d.stack(), stack)),
                "expected " + stack + " for " + to + " in " + deliveries);
    }

    @Test
    void hoistOnEmptyPoleCompletesAfterTheDelay() {
        Outcome start = FlagpoleMachine.use(FlagpoleState.EMPTY, new Input.Hoist(FlagKind.MERCHANT, merchant()), A, 100, DELAY);
        assertEquals(Feedback.STARTED_HOIST, start.feedback());
        assertEquals(FlagReading.NO_FLAG, start.state().reading());
        assertEquals(160, start.state().pending().orElseThrow().finishTick());
        assertEquals(Action.HOIST, start.state().pending().get().action());
        assertTrue(start.deliveries().isEmpty());
        assertTrue(start.cause().isEmpty());

        Outcome early = FlagpoleMachine.tick(start.state(), 159);
        assertEquals(start.state(), early.state());
        assertEquals(Feedback.NONE, early.feedback());

        Outcome done = FlagpoleMachine.tick(start.state(), 160);
        assertEquals(FlagReading.flying(FlagKind.MERCHANT), done.state().reading());
        assertEquals(Optional.of(A), done.state().hoistedBy());
        assertTrue(done.state().pending().isEmpty());
        assertTrue(done.deliveries().isEmpty());
        assertEquals(Feedback.HOISTED, done.feedback());
        assertEquals(Optional.of(Cause.HOISTED), done.cause());
        assertEquals(A, done.feedbackTo());
    }

    @Test
    void hoistOverAFlagGivesTheOldOneBackAndRaisesStruckColors() {
        Outcome done = run(FlagpoleMachine.use(flying(FlagKind.MERCHANT, merchant(), true), new Input.Hoist(FlagKind.NAVY, navy()), A, 0, DELAY));
        assertEquals(FlagReading.flying(FlagKind.NAVY), done.state().reading());
        assertEquals(1, done.deliveries().size());
        assertDelivered(done.deliveries(), A, merchant());
    }

    @Test
    void hoistTakesOneItem() {
        Outcome start = FlagpoleMachine.use(FlagpoleState.EMPTY, new Input.Hoist(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL, 5)), A, 0, DELAY);
        assertEquals(1, start.state().pending().orElseThrow().item().getCount());
    }

    @Test
    void zeroDelayCompletesAtOnce() {
        Outcome done = FlagpoleMachine.use(flying(FlagKind.MERCHANT, merchant(), false), new Input.Hoist(FlagKind.NAVY, navy()), A, 0, 0);
        assertEquals(FlagReading.flying(FlagKind.NAVY), done.state().reading());
        assertDelivered(done.deliveries(), A, merchant());
        assertEquals(Optional.of(Cause.HOISTED), done.cause());
        assertEquals(FlagReading.struck(FlagKind.NAVY),
                FlagpoleMachine.use(done.state(), new Input.Toggle(), A, 0, 0).state().reading());
    }

    @Test
    void toggleStrikesAndRaises() {
        FlagpoleState up = flying(FlagKind.JOLLY_ROGER, merchant(), false);
        Outcome start = FlagpoleMachine.use(up, new Input.Toggle(), A, 0, DELAY);
        assertEquals(Feedback.STARTED_STRIKE, start.feedback());
        assertEquals(FlagReading.flying(FlagKind.JOLLY_ROGER), start.state().reading());
        Outcome struck = run(start);
        assertEquals(FlagReading.struck(FlagKind.JOLLY_ROGER), struck.state().reading());
        assertEquals(FlagKind.NONE, struck.state().reading().shown());
        assertEquals(Optional.of(Cause.STRUCK), struck.cause());
        assertEquals(Optional.of(B), struck.state().hoistedBy(), "striking keeps who hoisted it");

        Outcome raiseStart = FlagpoleMachine.use(struck.state(), new Input.Toggle(), A, 0, DELAY);
        assertEquals(Feedback.STARTED_RAISE, raiseStart.feedback());
        Outcome raised = run(raiseStart);
        assertEquals(FlagReading.flying(FlagKind.JOLLY_ROGER), raised.state().reading());
        assertEquals(Optional.of(A), raised.state().hoistedBy());
        assertEquals(Optional.of(Cause.RAISED), raised.cause());
        assertTrue(raised.deliveries().isEmpty());
    }

    @Test
    void emptyHandOnBarePoleSaysNoFlag() {
        for (Input in : List.of(new Input.Toggle(), new Input.TakeDown())) {
            Outcome o = FlagpoleMachine.use(FlagpoleState.EMPTY, in, A, 0, DELAY);
            assertEquals(Feedback.NO_FLAG, o.feedback());
            assertEquals(FlagpoleState.EMPTY, o.state());
        }
    }

    @Test
    void takeDownReturnsTheFlag() {
        for (boolean struck : new boolean[]{false, true}) {
            Outcome start = FlagpoleMachine.use(flying(FlagKind.NAVY, navy(), struck), new Input.TakeDown(), A, 0, DELAY);
            assertEquals(Feedback.STARTED_TAKE_DOWN, start.feedback());
            Outcome done = run(start);
            assertEquals(FlagpoleState.EMPTY, done.state());
            assertDelivered(done.deliveries(), A, navy());
            assertEquals(Optional.of(Cause.TAKEN_DOWN), done.cause());
        }
    }

    @Test
    void emptyHandWhilePendingOnlyCancels() {
        Outcome start = FlagpoleMachine.use(FlagpoleState.EMPTY, new Input.Hoist(FlagKind.NAVY, navy()), A, 0, DELAY);
        for (Input in : List.of(new Input.Toggle(), new Input.TakeDown())) {
            Outcome o = FlagpoleMachine.use(start.state(), in, B, 10, DELAY);
            assertEquals(Feedback.CANCELLED, o.feedback());
            assertEquals(FlagpoleState.EMPTY, o.state());
            assertDelivered(o.deliveries(), A, navy());
            assertEquals(FlagpoleState.EMPTY, FlagpoleMachine.tick(o.state(), 1000).state());
        }
    }

    @Test
    void cancellingAStrikeReturnsNothing() {
        FlagpoleState up = flying(FlagKind.MERCHANT, merchant(), false);
        Outcome start = FlagpoleMachine.use(up, new Input.Toggle(), A, 0, DELAY);
        Outcome o = FlagpoleMachine.use(start.state(), new Input.Toggle(), A, 1, DELAY);
        assertEquals(Feedback.CANCELLED, o.feedback());
        assertEquals(up, o.state());
        assertTrue(o.deliveries().isEmpty());
    }

    @Test
    void anotherFlagWhilePendingReplacesTheHoist() {
        Outcome first = FlagpoleMachine.use(FlagpoleState.EMPTY, new Input.Hoist(FlagKind.MERCHANT, merchant()), A, 0, DELAY);
        Outcome second = FlagpoleMachine.use(first.state(), new Input.Hoist(FlagKind.NAVY, navy()), B, 30, DELAY);
        assertEquals(Feedback.STARTED_HOIST, second.feedback());
        assertDelivered(second.deliveries(), A, merchant());
        assertEquals(90, second.state().pending().orElseThrow().finishTick());
        assertEquals(FlagReading.flying(FlagKind.NAVY), run(second).state().reading());
    }

    @Test
    void cancelReturnsThePendingFlagToItsActor() {
        Outcome start = FlagpoleMachine.use(flying(FlagKind.MERCHANT, merchant(), false), new Input.Hoist(FlagKind.NAVY, navy()), A, 0, DELAY);
        Outcome o = FlagpoleMachine.cancel(start.state());
        assertEquals(flying(FlagKind.MERCHANT, merchant(), false), o.state());
        assertDelivered(o.deliveries(), A, navy());
        assertEquals(Feedback.NONE, FlagpoleMachine.cancel(o.state()).feedback());
    }

    @Test
    void breakingDropsFlagAndPendingFlag() {
        Outcome start = FlagpoleMachine.use(flying(FlagKind.MERCHANT, merchant(), true), new Input.Hoist(FlagKind.NAVY, navy()), A, 0, DELAY);
        Outcome broken = FlagpoleMachine.breakPole(start.state());
        assertEquals(FlagpoleState.EMPTY, broken.state());
        assertEquals(2, broken.deliveries().size());
        assertDelivered(broken.deliveries(), null, merchant());
        assertDelivered(broken.deliveries(), null, navy());
        assertEquals(Optional.of(Cause.BROKEN), broken.cause());
        assertTrue(FlagpoleMachine.breakPole(FlagpoleState.EMPTY).cause().isEmpty());
    }

    @Test
    void commandSetDropsOldItemsAndCancels() {
        Outcome start = FlagpoleMachine.use(flying(FlagKind.MERCHANT, merchant(), false), new Input.Hoist(FlagKind.NAVY, navy()), A, 0, DELAY);
        Outcome set = FlagpoleMachine.set(start.state(), FlagKind.JOLLY_ROGER, new ItemStack(Items.BLACK_WOOL), true, null);
        assertEquals(FlagReading.struck(FlagKind.JOLLY_ROGER), set.state().reading());
        assertTrue(set.state().pending().isEmpty());
        assertEquals(2, set.deliveries().size());
        assertEquals(FlagpoleState.EMPTY, FlagpoleMachine.set(set.state(), FlagKind.NONE, ItemStack.EMPTY, false, null).state());

        Outcome strike = FlagpoleMachine.setStruck(flying(FlagKind.NAVY, navy(), false), true);
        assertEquals(FlagReading.struck(FlagKind.NAVY), strike.state().reading());
        assertEquals(Optional.of(Cause.COMMAND), strike.cause());
        assertTrue(FlagpoleMachine.setStruck(strike.state(), true).cause().isEmpty());
        assertTrue(FlagpoleMachine.setStruck(FlagpoleState.EMPTY, true).cause().isEmpty());
    }

    @Test
    void hoistNeedsAFlag() {
        assertThrows(IllegalArgumentException.class, () -> new Input.Hoist(FlagKind.NONE, merchant()));
        assertThrows(IllegalArgumentException.class, () -> new Input.Hoist(FlagKind.NAVY, ItemStack.EMPTY));
    }

    @Test
    void stateNormalizesAFlaglessPole() {
        FlagpoleState s = new FlagpoleState(FlagKind.NAVY, ItemStack.EMPTY, true, Optional.empty(), Optional.empty());
        assertFalse(s.hasFlag());
        assertFalse(s.struck());
        assertEquals(FlagReading.NO_FLAG, s.reading());
    }
}
