package com.richardsenger.piratesnships.law.net;

import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.LawService;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;
import com.richardsenger.piratesnships.rpg.quest.QuestData;
import com.richardsenger.piratesnships.rpg.quest.QuestRules;
import com.richardsenger.piratesnships.rpg.quest.Quests;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * What a notice board has posted (NB2), counted for the market board's look ({@code load}): every bounty line the
 * board's screen lists ({@link NoticeBoardListing#lines}, the same for every board) plus the open quest offers of the
 * port the board stands in (the offers the port's desk shows, contracts included as cargo runs; none outside a port).
 * Read only: counting never makes a port's offers of the day. With {@code law.bounty.notice_boards} off nothing is
 * posted.
 *
 * <p>{@link #revision()} goes up whenever the backend posts something ({@link #changed()}), so a board recounts at
 * once instead of waiting for {@code law.notice_board.refresh_ticks}.
 */
public final class NoticeBoardPostings {

    private static final AtomicLong REVISION = new AtomicLong();

    private NoticeBoardPostings() {
    }

    /** The open postings a board at {@code pos} shows. */
    public static int count(ServerLevel level, BlockPos pos) {
        if (!LawConfig.NOTICE_BOARDS.get()) return 0;
        return bounties(level.getServer()) + questOffers(level, pos);
    }

    /** Bounty lines on every board. */
    public static int bounties(MinecraftServer server) {
        return NoticeBoardListing.lines(LawService.board(server), LawService.now(server)).size();
    }

    /** The unexpired quest offers of the port whose box holds {@code pos}; 0 outside a port or with quests off. */
    public static int questOffers(ServerLevel level, BlockPos pos) {
        if (!Quests.enabled()) return 0;
        MinecraftServer server = level.getServer();
        Optional<Port> port = PortRegistry.get(server).index().containing(level.dimension(), pos);
        if (port.isEmpty()) return 0;
        long day = Quests.day(server);
        return (int) QuestData.get(server).offers(port.get().id()).stream().filter(q -> !QuestRules.expired(q, day)).count();
    }

    /** Something was posted or taken down: boards recount on their next tick. */
    public static void changed() {
        REVISION.incrementAndGet();
    }

    public static long revision() {
        return REVISION.get();
    }
}
