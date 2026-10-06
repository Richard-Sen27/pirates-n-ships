package com.richardsenger.piratesnships.law;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import com.richardsenger.piratesnships.law.bounty.BountyRules;
import com.richardsenger.piratesnships.law.bounty.BountyTarget;
import com.richardsenger.piratesnships.law.crime.CrimeRules;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LawCodecTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static <T> void roundTrip(Codec<T> codec, T value) {
        var nbt = codec.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        assertEquals(value, codec.parse(NbtOps.INSTANCE, nbt).getOrThrow());
        var json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        assertEquals(value, codec.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void criminalRecord() {
        CrimeRules rules = CrimeRules.defaults();
        roundTrip(CriminalRecord.CODEC, CriminalRecord.EMPTY);
        var r = CriminalRecord.EMPTY.addCrime(CrimeType.ATTACK_NAVY, UUID.randomUUID(), 100, rules).record()
                .addCrime(CrimeType.SEEN_UNDER_JOLLY_ROGER, null, 120, rules).record()
                .decayTo(10_000, rules);
        roundTrip(CriminalRecord.CODEC, r.withScore(12.345));
    }

    @Test
    void emptyTagGivesEmptyRecord() {
        assertEquals(CriminalRecord.EMPTY, CriminalRecord.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow());
    }

    @Test
    void bountyBoard() {
        BountyRules rules = BountyRules.defaults().withPlayerBounties(true, 1, 24000);
        var target = BountyTarget.player(UUID.randomUUID(), "Blackbeard");
        var board = BountyBoard.EMPTY.syncNavy(target, 80, 5, rules).board()
                .placePlayerBounty(UUID.randomUUID(), UUID.randomUUID(), "Anne", target, 25, 7, rules).board()
                .placePlayerBounty(UUID.randomUUID(), UUID.randomUUID(), "Mary",
                        BountyTarget.npc(UUID.randomUUID(), "Pirate"), 40, 9, rules.withPlayerBounties(true, 1, 0)).board();
        roundTrip(BountyBoard.CODEC, board);
        roundTrip(BountyBoard.CODEC, BountyBoard.EMPTY);
    }

    @Test
    void enumsUseSnakeCaseIds() {
        assertEquals("\"jolly_roger\"", FlagKind.CODEC.encodeStart(JsonOps.INSTANCE, FlagKind.JOLLY_ROGER).getOrThrow().toString());
        assertEquals("\"caught_false_colors\"",
                CrimeType.CODEC.encodeStart(JsonOps.INSTANCE, CrimeType.CAUGHT_FALSE_COLORS).getOrThrow().toString());
    }
}
