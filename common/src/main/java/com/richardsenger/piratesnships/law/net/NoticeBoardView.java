package com.richardsenger.piratesnships.law.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.richardsenger.piratesnships.law.bounty.NoticeBoardListing;

import java.util.List;

/**
 * What a notice board screen shows (docs/design.md §13.2), sent by the server in {@link NoticeBoardPayloads.State}.
 *
 * @param lines    the active bounties ({@link NoticeBoardListing#lines})
 * @param ownTotal the bounty on the viewer's own head (0 = none)
 * @param coins    the viewer's doubloons
 * @param minimum  the smallest bounty a player may place
 * @param placing  whether players may place bounties ({@code law.player_bounties})
 * @param names    names the place form offers ({@link NoticeBoardListing#names})
 * @param now      the server's game time when this was made (for "placed ... ago")
 */
public record NoticeBoardView(List<NoticeBoardListing.Line> lines, long ownTotal, long coins, int minimum, boolean placing,
                              List<String> names, long now) {

    public static final Codec<NoticeBoardView> CODEC = RecordCodecBuilder.create(i -> i.group(
            NoticeBoardListing.Line.CODEC.listOf().fieldOf("lines").forGetter(NoticeBoardView::lines),
            Codec.LONG.fieldOf("own_total").forGetter(NoticeBoardView::ownTotal),
            Codec.LONG.fieldOf("coins").forGetter(NoticeBoardView::coins),
            Codec.INT.fieldOf("minimum").forGetter(NoticeBoardView::minimum),
            Codec.BOOL.fieldOf("placing").forGetter(NoticeBoardView::placing),
            Codec.STRING.listOf().fieldOf("names").forGetter(NoticeBoardView::names),
            Codec.LONG.fieldOf("now").forGetter(NoticeBoardView::now)
    ).apply(i, NoticeBoardView::new));

    public NoticeBoardView {
        lines = List.copyOf(lines);
        names = List.copyOf(names);
    }

    /** Equal apart from {@link #now}: nothing a viewer would notice changed, so there is no need to resend. */
    public boolean sameContent(NoticeBoardView other) {
        return other != null && lines.equals(other.lines) && ownTotal == other.ownTotal && coins == other.coins
                && minimum == other.minimum && placing == other.placing && names.equals(other.names);
    }
}
