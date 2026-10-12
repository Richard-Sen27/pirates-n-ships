package com.richardsenger.piratesnships.law.content;

import com.richardsenger.piratesnships.law.LawConfig;
import com.richardsenger.piratesnships.law.net.NoticeBoardPostings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The market notice board's anchor (NB2): counts the board's open postings ({@link NoticeBoardPostings#count}) on its
 * first tick after loading, every {@code law.notice_board.refresh_ticks} and as soon as the backend posts something,
 * and sets the four parts' {@code load} from {@link NoticeBoardLoad#of}. Saves nothing: the load is in the block state.
 */
public class MarketNoticeBoardBlockEntity extends BlockEntity {

    /** The postings revision last counted; -1 makes the first tick count. */
    private long seenRevision = -1;
    private int countdown;

    public MarketNoticeBoardBlockEntity(BlockPos pos, BlockState state) {
        super(LawContent.MARKET_NOTICE_BOARD_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MarketNoticeBoardBlockEntity be) {
        long revision = NoticeBoardPostings.revision();
        if (revision == be.seenRevision && --be.countdown > 0) return;
        be.seenRevision = revision;
        be.countdown = LawConfig.NOTICE_BOARD_REFRESH_TICKS.get();
        if (level instanceof ServerLevel server) refresh(server, pos, state);
    }

    /** Counts now and sets the load on every part; returns the load shown. */
    public static NoticeBoardLoad refresh(ServerLevel level, BlockPos anchor, BlockState state) {
        NoticeBoardLoad load = NoticeBoardLoad.of(NoticeBoardPostings.count(level, anchor), LawConfig.NOTICE_BOARD_BUSY_THRESHOLD.get());
        MarketNoticeBoardBlock.applyLoad(level, anchor, state.getValue(MarketNoticeBoardBlock.FACING), load);
        return load;
    }
}
