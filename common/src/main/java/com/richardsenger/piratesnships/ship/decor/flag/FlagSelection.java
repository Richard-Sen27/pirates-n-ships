package com.richardsenger.piratesnships.ship.decor.flag;

import com.richardsenger.piratesnships.law.flag.FlagKind;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Comparator;

/**
 * Which flag a ship with several flagpoles shows (pure). The rule:
 * <ol>
 *   <li>Poles without a flag are ignored. No flag anywhere gives {@link FlagReading#NO_FLAG}.</li>
 *   <li>The <b>highest</b> pole with a flag decides (largest y), like the masthead flag of a real ship.</li>
 *   <li>Between poles at the same height, a struck flag wins: lowering any top flag reads as surrender.</li>
 *   <li>Then the more telling flag wins: Jolly Roger, navy, custom, merchant.</li>
 *   <li>Last, the smallest x, then z, so the result never depends on iteration order.</li>
 * </ol>
 */
public final class FlagSelection {

    /** One flagpole of a ship and what it shows. */
    public record PoleReading(BlockPos pos, FlagReading reading) {
    }

    private static final Comparator<PoleReading> PRIORITY = Comparator
            .comparingInt((PoleReading p) -> p.pos().getY())
            .thenComparing(p -> p.reading().isStruck())
            .thenComparingInt(p -> telling(p.reading().kind()))
            .thenComparing(Comparator.comparingInt((PoleReading p) -> p.pos().getX()).reversed())
            .thenComparing(Comparator.comparingInt((PoleReading p) -> p.pos().getZ()).reversed());

    private FlagSelection() {
    }

    public static FlagReading pick(Collection<PoleReading> poles) {
        return poles.stream().filter(p -> p.reading().hasFlag()).max(PRIORITY).map(PoleReading::reading).orElse(FlagReading.NO_FLAG);
    }

    private static int telling(FlagKind kind) {
        return switch (kind) {
            case JOLLY_ROGER -> 4;
            case NAVY -> 3;
            case CUSTOM -> 2;
            case MERCHANT -> 1;
            case NONE -> 0;
        };
    }
}
