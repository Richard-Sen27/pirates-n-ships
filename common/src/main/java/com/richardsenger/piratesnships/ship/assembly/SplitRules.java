package com.richardsenger.piratesnships.ship.assembly;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * What happens to the pieces of a ship that Sable split (RS1, docs/design.md §4.6). Pure rules, no world access.
 *
 * <ul>
 *   <li><b>Keeper:</b> the piece that holds the helm keeps the ship's identity. If several or none hold one, the
 *       larger keeps it. On a tie the piece Sable left in the original sub-level wins (it already has the ship's id;
 *       Sable leaves the part connected to the original's first block there, or the largest part when every block was
 *       cut off, see {@code SableSplits}), else the first in the given order.</li>
 *   <li><b>Tiny:</b> a piece other than the keeper with fewer than {@code minBlocks} blocks is broken up into items.</li>
 *   <li><b>Origin:</b> every piece remembers the first ship of its line: the parent's own origin if it has one (a piece of
 *       a piece), else the parent's id.</li>
 * </ul>
 */
public final class SplitRules {

    /**
     * One piece after a split.
     *
     * @param id       the piece's ship id (sub-level UUID)
     * @param blocks   its block count
     * @param helms    helm blocks on it
     * @param original true for the piece that is still in the original sub-level (it keeps the original's id)
     */
    public record Piece(UUID id, int blocks, int helms, boolean original) { }

    private SplitRules() {
    }

    /** The id of the piece that keeps the ship's identity. {@code pieces} must not be empty. */
    public static UUID keeper(List<Piece> pieces) {
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("no pieces");
        }
        List<Piece> withHelm = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.helms() > 0) {
                withHelm.add(p);
            }
        }
        List<Piece> candidates = withHelm.isEmpty() ? pieces : withHelm;
        Piece best = null;
        for (Piece p : candidates) {
            if (best == null || p.blocks() > best.blocks() || p.blocks() == best.blocks() && p.original() && !best.original()) {
                best = p;
            }
        }
        return best.id();
    }

    /** Whether a piece is broken up into items: not the keeper and fewer than {@code minBlocks} blocks. */
    public static boolean drops(Piece piece, boolean keeper, int minBlocks) {
        return !keeper && piece.blocks() < minBlocks;
    }

    /** The origin of every piece split from {@code parentId}: the first ship of the line. */
    public static UUID origin(@Nullable UUID parentOrigin, UUID parentId) {
        return parentOrigin != null ? parentOrigin : parentId;
    }
}
