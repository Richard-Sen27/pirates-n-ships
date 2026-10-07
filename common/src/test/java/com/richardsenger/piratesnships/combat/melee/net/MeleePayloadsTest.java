package com.richardsenger.piratesnships.combat.melee.net;

import com.richardsenger.piratesnships.combat.melee.rules.AttackKind;
import com.richardsenger.piratesnships.combat.melee.rules.CombatRules;
import com.richardsenger.piratesnships.combat.melee.rules.CombatState;
import com.richardsenger.piratesnships.combat.melee.rules.MeleeParams;
import com.richardsenger.piratesnships.combat.melee.rules.Phase;
import com.richardsenger.piratesnships.combat.melee.rules.Refusal;
import com.richardsenger.piratesnships.combat.melee.weapon.DefaultWeapons;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class MeleePayloadsTest {

    static final MeleeParams P = MeleeParams.DEFAULTS;

    static MeleeStatePayload roundTrip(MeleeStatePayload p) {
        ByteBuf buf = Unpooled.buffer();
        MeleeStatePayload.CODEC.encode(buf, p);
        MeleeStatePayload back = MeleeStatePayload.CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "everything read");
        return back;
    }

    @ParameterizedTest
    @EnumSource(MeleeAction.class)
    void actionPayloadRoundTrips(MeleeAction action) {
        ByteBuf buf = Unpooled.buffer();
        MeleeActionPayload p = new MeleeActionPayload(action, 123456);
        MeleeActionPayload.CODEC.encode(buf, p);
        MeleeActionPayload back = MeleeActionPayload.CODEC.decode(buf);
        assertEquals(p, back);
        assertEquals(action, back.decodedAction());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void unknownActionDecodesToNull() {
        assertNull(new MeleeActionPayload(99, 0).decodedAction());
        assertNull(new MeleeActionPayload(-1, 0).decodedAction());
    }

    @ParameterizedTest
    @EnumSource(Phase.class)
    void statePayloadRoundTripsEveryPhase(Phase phase) {
        MeleeStatePayload p = new MeleeStatePayload(42, phase, phase.attacking() ? AttackKind.THRUST : null, 3, 9,
                true, phase.attacking(), 17, 5, 61.5f, 100f, Refusal.LOCKED_OUT, 987654);
        assertEquals(p, roundTrip(p));
    }

    @Test
    void ownerCopyCarriesStaminaAndRefusal() {
        CombatState s = CombatRules.startAttack(CombatState.fresh(P.staminaMax()), AttackKind.SLASH, DefaultWeapons.CUTLASS, P).state();
        MeleeStatePayload p = MeleeStatePayload.owned(7, s, P.staminaMax(), Refusal.BUSY, 50);
        assertTrue(p.hasStamina());
        assertEquals(s.stamina(), p.stamina());
        assertEquals(Phase.WINDUP, p.phase());
        assertEquals(AttackKind.SLASH, p.attack());
        assertEquals(Refusal.BUSY, p.refusal());
        assertEquals(p, roundTrip(p));
    }

    @Test
    void observerCopyHasNoStaminaAndNoRefusal() {
        CombatState s = CombatRules.guardDown(CombatState.fresh(P.staminaMax()), DefaultWeapons.SABER, P).state();
        MeleeStatePayload p = MeleeStatePayload.observed(7, s, 50);
        assertFalse(p.hasStamina());
        assertEquals(0f, p.stamina());
        assertEquals(Refusal.NONE, p.refusal());
        assertTrue(p.guardHeld());
        assertEquals(Phase.GUARDING, p.phase());
        assertEquals(p, roundTrip(p));
    }

    @Test
    void staggerTicksAreTheRemainingStagger() {
        MeleeStatePayload staggered = new MeleeStatePayload(1, Phase.STAGGERED, null, 5, 20, false, false, 0, 0, 0f, 0f, Refusal.NONE, 0);
        assertEquals(15, staggered.staggerTicks());
        MeleeStatePayload idle = new MeleeStatePayload(1, Phase.IDLE, null, 5, 20, false, false, 0, 0, 0f, 0f, Refusal.NONE, 0);
        assertEquals(0, idle.staggerTicks());
    }

    @Test
    void refusalMessagesOnlyForRefusalsWorthANote() {
        assertNotNull(MeleeActions.refusalMessageKey(Refusal.NO_STAMINA));
        assertNotNull(MeleeActions.refusalMessageKey(Refusal.LOCKED_OUT));
        assertNotNull(MeleeActions.refusalMessageKey(Refusal.STAGGERED));
        assertNull(MeleeActions.refusalMessageKey(Refusal.BUSY), "mashing during an attack stays silent");
        assertNull(MeleeActions.refusalMessageKey(Refusal.NONE));
        assertNull(MeleeActions.refusalMessageKey(Refusal.DISABLED));
    }
}
