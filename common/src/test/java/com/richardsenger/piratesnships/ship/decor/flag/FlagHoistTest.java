package com.richardsenger.piratesnships.ship.decor.flag;

import com.mojang.serialization.JsonOps;
import com.google.gson.JsonObject;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState.Action;
import com.richardsenger.piratesnships.ship.decor.flag.FlagpoleState.Pending;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The cloth's place on the pole while an action is pending (VIS1a), and the pending action's start tick. */
class FlagHoistTest {

    private static final double EPS = 1e-9;
    private static final UUID ACTOR = UUID.randomUUID();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FlagpoleState flying(FlagKind kind, ItemStack item, boolean struck, Optional<Pending> pending) {
        return new FlagpoleState(kind, item, struck, Optional.empty(), pending);
    }

    private static Optional<Pending> pending(Action action, long start, long finish, FlagKind kind, ItemStack item) {
        return Optional.of(new Pending(action, ACTOR, start, finish, kind, item));
    }

    @Test
    void travelRunsFromTheFootToTheHead() {
        assertEquals(FlagHoist.FOOT_DIP, FlagHoist.travel(1), EPS);
        assertEquals(5 + FlagHoist.FOOT_DIP, FlagHoist.travel(6), EPS);
        assertEquals(FlagHoist.FOOT_DIP, FlagHoist.travel(0), EPS, "a height below one counts as one block");
    }

    @Test
    void progressIsClampedAndLinear() {
        assertEquals(0.0, FlagHoist.progress(100, 160, 90), EPS);
        assertEquals(0.0, FlagHoist.progress(100, 160, 100), EPS);
        assertEquals(0.5, FlagHoist.progress(100, 160, 130), EPS);
        assertEquals(0.25, FlagHoist.progress(100, 160, 115), EPS);
        assertEquals(1.0, FlagHoist.progress(100, 160, 160), EPS);
        assertEquals(1.0, FlagHoist.progress(100, 160, 500), EPS);
        assertEquals(1.0, FlagHoist.progress(160, 160, 100), EPS, "no length (an old save): done");
    }

    @Test
    void hoistAndRaiseRiseStrikeAndTakeDownFall() {
        int h = 5;
        double t = FlagHoist.travel(h);
        for (Action a : new Action[]{Action.HOIST, Action.RAISE}) {
            assertEquals(t, FlagHoist.drop(a, 0, 40, 0, h), EPS, a + " starts at the foot");
            assertEquals(t / 2, FlagHoist.drop(a, 0, 40, 20, h), EPS, a + " is halfway at half time");
            assertEquals(0, FlagHoist.drop(a, 0, 40, 40, h), EPS, a + " ends at the head");
        }
        for (Action a : new Action[]{Action.STRIKE, Action.TAKE_DOWN}) {
            assertEquals(0, FlagHoist.drop(a, 0, 40, 0, h), EPS, a + " starts at the head");
            assertEquals(t * 0.75, FlagHoist.drop(a, 0, 40, 30, h), EPS);
            assertEquals(t, FlagHoist.drop(a, 0, 40, 40, h), EPS, a + " ends at the foot");
        }
    }

    @Test
    void aHoistDrawsTheFlagBeingHoistedWithItsTint() {
        ItemStack banner = new ItemStack(Items.GREEN_BANNER);
        FlagpoleState s = flying(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL), false,
                pending(Action.HOIST, 100, 160, FlagKind.CUSTOM, banner));
        FlagHoist.Frame f = FlagHoist.frame(s, 130, 3, true);
        assertEquals(FlagKind.CUSTOM, f.kind());
        assertEquals(FlagHoist.travel(3) / 2, f.drop(), EPS);
        assertEquals(FlagTint.rgb(DyeColor.GREEN), f.tint());
    }

    @Test
    void strikeAndTakeDownDrawTheCurrentFlagFalling() {
        ItemStack merchant = new ItemStack(Items.WHITE_WOOL);
        for (Action a : new Action[]{Action.STRIKE, Action.TAKE_DOWN}) {
            FlagpoleState s = flying(FlagKind.MERCHANT, merchant, false, pending(a, 0, 20, FlagKind.NONE, ItemStack.EMPTY));
            FlagHoist.Frame f = FlagHoist.frame(s, 10, 4, true);
            assertEquals(FlagKind.MERCHANT, f.kind(), a.name());
            assertEquals(FlagHoist.travel(4) / 2, f.drop(), EPS, a.name());
        }
    }

    @Test
    void aRaiseDrawsTheStruckFlagRising() {
        FlagpoleState s = flying(FlagKind.JOLLY_ROGER, new ItemStack(Items.BLACK_WOOL), true,
                pending(Action.RAISE, 0, 20, FlagKind.NONE, ItemStack.EMPTY));
        FlagHoist.Frame f = FlagHoist.frame(s, 5, 2, true);
        assertEquals(FlagKind.JOLLY_ROGER, f.kind());
        assertEquals(FlagHoist.travel(2) * 0.75, f.drop(), EPS);
    }

    @Test
    void struckOrIdlePolesShowTheirState() {
        FlagpoleState struck = flying(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL), true, Optional.empty());
        assertSame(FlagHoist.Frame.NOTHING, FlagHoist.frame(struck, 0, 3, true), "after a strike nothing is drawn");
        FlagpoleState takingDownStruck = flying(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL), true,
                pending(Action.TAKE_DOWN, 0, 20, FlagKind.NONE, ItemStack.EMPTY));
        assertSame(FlagHoist.Frame.NOTHING, FlagHoist.frame(takingDownStruck, 10, 3, true), "a struck flag is down already");
        FlagpoleState idle = flying(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL), false, Optional.empty());
        FlagHoist.Frame f = FlagHoist.frame(idle, 0, 3, true);
        assertEquals(FlagKind.NAVY, f.kind());
        assertEquals(0, f.drop(), EPS);
        assertSame(FlagHoist.Frame.NOTHING, FlagHoist.frame(FlagpoleState.EMPTY, 0, 3, true));
    }

    @Test
    void withoutTheAnimationThePoleShowsItsStateAtTheHead() {
        FlagpoleState hoisting = flying(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL), false,
                pending(Action.HOIST, 0, 20, FlagKind.MERCHANT, new ItemStack(Items.WHITE_WOOL)));
        FlagHoist.Frame f = FlagHoist.frame(hoisting, 10, 5, false);
        assertEquals(FlagKind.NAVY, f.kind());
        assertEquals(0, f.drop(), EPS);
        FlagpoleState striking = flying(FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL), false,
                pending(Action.STRIKE, 0, 20, FlagKind.NONE, ItemStack.EMPTY));
        assertEquals(0, FlagHoist.frame(striking, 15, 5, false).drop(), EPS);
    }

    @Test
    void theDrawingBlockHoldsTheClothsMiddle() {
        assertEquals(0, FlagHoist.drawerBelowHead(0, 4));
        assertEquals(0, FlagHoist.drawerBelowHead(0.49, 4));
        assertEquals(1, FlagHoist.drawerBelowHead(0.5, 4));
        assertEquals(1, FlagHoist.drawerBelowHead(1.4, 4));
        assertEquals(3, FlagHoist.drawerBelowHead(3.25, 4), "the foot draws the dipped cloth");
        assertEquals(3, FlagHoist.drawerBelowHead(9, 4), "never below the foot");
        assertEquals(0, FlagHoist.drawerBelowHead(FlagHoist.travel(1), 1), "a one-block pole draws itself");
    }

    @Test
    void pendingWithoutStartTickLoadsAsUnanimated() {
        Pending p = new Pending(Action.HOIST, ACTOR, 100, 160, FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL));
        JsonObject json = Pending.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow().getAsJsonObject();
        assertEquals(100, json.get("start_tick").getAsLong());
        assertEquals(p, Pending.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        json.remove("start_tick");
        Pending old = Pending.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(160, old.startTick(), "a pole saved before VIS1a has no start: it draws no animation");
        assertEquals(160, old.finishTick());
        assertEquals(old, new Pending(Action.HOIST, ACTOR, 160, FlagKind.NAVY, new ItemStack(Items.BLUE_WOOL)));
    }
}
