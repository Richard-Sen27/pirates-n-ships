package com.richardsenger.piratesnships.law.brig;

import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.law.brig.CaptureRules.Result;
import com.richardsenger.piratesnships.law.brig.EscapeRule.Decision;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Capture eligibility, escapes (seeded), ransom, door lock and the prisoner state codecs. */
class BrigRulesTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // --- Capture ------------------------------------------------------------------------------------------------

    private static CaptureRules.Target mob(float health) {
        return new CaptureRules.Target(false, true, true, health, 20f, false, false, false);
    }

    @Test
    void captureNeedsLowHealth() {
        assertEquals(Result.TOO_HEALTHY, CaptureRules.check(mob(20f), 0.25, true));
        assertEquals(Result.TOO_HEALTHY, CaptureRules.check(mob(5.1f), 0.25, true));
        assertEquals(Result.OK, CaptureRules.check(mob(5f), 0.25, true));
        assertEquals(Result.OK, CaptureRules.check(mob(1f), 0.25, true));
    }

    @Test
    void captureRefusals() {
        assertEquals(Result.NOT_CAPTURABLE, CaptureRules.check(new CaptureRules.Target(false, false, true, 1f, 20f, false, false, false), 0.25, true));
        assertEquals(Result.DEAD, CaptureRules.check(new CaptureRules.Target(false, true, false, 0f, 20f, false, false, false), 0.25, true));
        assertEquals(Result.ALREADY_PRISONER, CaptureRules.check(new CaptureRules.Target(false, true, true, 1f, 20f, true, false, false), 0.25, true));
        assertEquals(Result.SELF, CaptureRules.check(new CaptureRules.Target(true, false, true, 1f, 20f, false, true, true), 0.25, true));
    }

    @Test
    void playersNeedToggleAndBounty() {
        CaptureRules.Target wanted = new CaptureRules.Target(true, false, true, 2f, 20f, false, false, true);
        CaptureRules.Target clean = new CaptureRules.Target(true, false, true, 2f, 20f, false, false, false);
        assertEquals(Result.OK, CaptureRules.check(wanted, 0.25, true));
        assertEquals(Result.PLAYER_CAPTURE_DISABLED, CaptureRules.check(wanted, 0.25, false));
        assertEquals(Result.NO_BOUNTY, CaptureRules.check(clean, 0.25, true));
        assertEquals(Result.TOO_HEALTHY, CaptureRules.check(new CaptureRules.Target(true, false, true, 20f, 20f, false, false, true), 0.25, true));
    }

    // --- Escapes ------------------------------------------------------------------------------------------------

    private static final EscapeRule.Params HALF = new EscapeRule.Params(0.5, 0.1);

    private static int escapes(EscapeRule.Inputs in, EscapeRule.Params p, long seed) {
        RandomSource r = RandomSource.create(seed);
        int n = 0;
        for (int i = 0; i < 1000; i++) if (EscapeRule.decide(in, p, r) == Decision.ESCAPE) n++;
        return n;
    }

    @Test
    void escapeOutsideCellAtConfiguredChance() {
        int n = escapes(new EscapeRule.Inputs(true, false, false, false, false), HALF, 42L);
        assertTrue(n > 430 && n < 570, "about half, got " + n);
        assertEquals(n, escapes(new EscapeRule.Inputs(true, false, false, false, false), HALF, 42L), "seeded = repeatable");
    }

    @Test
    void lockedCellLedOrDisabledMeansNoEscape() {
        assertEquals(0, escapes(new EscapeRule.Inputs(true, true, false, false, false), HALF, 1L));
        assertEquals(0, escapes(new EscapeRule.Inputs(true, false, true, false, false), HALF, 1L));
        assertEquals(0, escapes(new EscapeRule.Inputs(false, false, false, false, false), new EscapeRule.Params(1.0, 1.0), 1L));
    }

    @Test
    void lowMoraleAllowsEscapeFromCell() {
        int n = escapes(new EscapeRule.Inputs(true, true, false, true, false), HALF, 7L);
        assertTrue(n > 60 && n < 140, "about a tenth, got " + n);
    }

    @Test
    void ownFactionFreesEvenWhenDisabled() {
        assertEquals(Decision.FREED, EscapeRule.decide(new EscapeRule.Inputs(false, true, true, false, true), HALF, RandomSource.create(0)));
    }

    @Test
    void chanceOneAlwaysEscapes() {
        assertEquals(1000, escapes(new EscapeRule.Inputs(true, false, false, false, false), new EscapeRule.Params(1.0, 0.0), 3L));
    }

    @Test
    void perMinuteToPerCheck() {
        assertEquals(0.0, EscapeRule.perCheck(0.0, 20));
        assertEquals(1.0, EscapeRule.perCheck(1.0, 20));
        assertEquals(0.1, EscapeRule.perCheck(0.1, 1200), 1e-12);
        // 60 checks of a minute compound back to the per-minute chance
        double p = EscapeRule.perCheck(0.1, 20);
        assertEquals(0.1, 1.0 - Math.pow(1.0 - p, 60), 1e-9);
    }

    // --- Ransom and door ----------------------------------------------------------------------------------------

    @Test
    void ransomByKindAndCaptain() {
        RansomRules.Params p = new RansomRules.Params(15, 60, 120, 2.5);
        assertEquals(15, RansomRules.ransom(RansomRules.Kind.COMMON, false, p));
        assertEquals(60, RansomRules.ransom(RansomRules.Kind.MERCHANT, false, p));
        assertEquals(120, RansomRules.ransom(RansomRules.Kind.NAVY_OFFICER, false, p));
        assertEquals(300, RansomRules.ransom(RansomRules.Kind.NAVY_OFFICER, true, p));
        assertEquals(38, RansomRules.ransom(RansomRules.Kind.COMMON, true, p));
    }

    @Test
    void doorLock() {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(DoorLockRules.canOpen(false, owner, other, false));
        assertTrue(DoorLockRules.canOpen(false, owner, null, false), "unlocked: mobs and redstone too");
        assertTrue(DoorLockRules.canOpen(true, owner, owner, false));
        assertFalse(DoorLockRules.canOpen(true, owner, other, false));
        assertTrue(DoorLockRules.canOpen(true, owner, other, true), "extra access (crew later)");
        assertFalse(DoorLockRules.canOpen(true, owner, null, true), "never mobs or redstone");
        assertTrue(DoorLockRules.canChangeLock(null, other, false), "ownerless door: anyone");
        assertFalse(DoorLockRules.canChangeLock(owner, other, false));
        assertTrue(DoorLockRules.canChangeLock(owner, owner, false));
    }

    // --- Codecs -------------------------------------------------------------------------------------------------

    @Test
    void prisonerStateCodecRoundTrip() {
        PrisonerState st = new PrisonerState(UUID.randomUUID(), "Anne", 1234L, true, 9999L);
        var json = PrisonerState.CODEC.encodeStart(JsonOps.INSTANCE, st).getOrThrow();
        assertEquals(st, PrisonerState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        ByteBuf buf = Unpooled.buffer();
        PrisonerState.STREAM_CODEC.encode(buf, st);
        assertEquals(st, PrisonerState.STREAM_CODEC.decode(buf));
        ByteBuf none = Unpooled.buffer();
        PrisonerState.STREAM_CODEC.encode(none, PrisonerState.NONE);
        assertEquals(PrisonerState.NONE, PrisonerState.STREAM_CODEC.decode(none));
        assertFalse(PrisonerState.NONE.active());
        assertTrue(st.active() && st.heldBy(st.captor()));
    }

    @Test
    void playerTimeLimit() {
        PrisonerState player = PrisonerState.captured(UUID.randomUUID(), "x", 100L, 700L);
        assertFalse(player.expired(699L));
        assertTrue(player.expired(700L));
        assertFalse(PrisonerState.captured(UUID.randomUUID(), "x", 100L, -1L).expired(Long.MAX_VALUE), "NPCs have no limit");
    }
}
