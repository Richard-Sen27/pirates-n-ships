package com.richardsenger.piratesnships.law.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.List;

/**
 * The four parts of the market notice board (NB2, docs/design.md §13.2 "Village life"), a 2 wide × 2 high board. Left
 * and right are the viewer's, who stands on the notices' side ({@code facing}) looking at the board: the viewer's right
 * is {@code facing.getCounterClockWise()}. The bottom-left part is the anchor: it is where the board was placed, it
 * holds the block entity and only its loot drops the board. Pure arithmetic, no world access.
 */
public enum MarketBoardPart implements StringRepresentable {
    BOTTOM_LEFT("bottom_left", 0, 0),
    BOTTOM_RIGHT("bottom_right", 1, 0),
    TOP_LEFT("top_left", 0, 1),
    TOP_RIGHT("top_right", 1, 1);

    private final String name;
    private final int column;
    private final int row;

    MarketBoardPart(String name, int column, int row) {
        this.name = name;
        this.column = column;
        this.row = row;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** 0 on the viewer's left, 1 on the right. */
    public int column() {
        return column;
    }

    /** 0 at the bottom, 1 on top. */
    public int row() {
        return row;
    }

    public boolean isAnchor() {
        return this == BOTTOM_LEFT;
    }

    public static MarketBoardPart at(int column, int row) {
        for (MarketBoardPart p : values()) if (p.column == column && p.row == row) return p;
        throw new IllegalArgumentException("no part at column " + column + ", row " + row);
    }

    /** The viewer's right for a board whose notices face {@code facing}. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The anchor's position for this part standing at {@code pos}. */
    public BlockPos anchor(BlockPos pos, Direction facing) {
        return pos.relative(right(facing), -column).below(row);
    }

    /** Where {@code other} stands when this part stands at {@code pos}. */
    public BlockPos posOf(MarketBoardPart other, BlockPos pos, Direction facing) {
        return anchor(pos, facing).relative(right(facing), other.column).above(other.row);
    }

    /** Every part's position for the anchor at {@code anchor}, in {@link #values()} order. */
    public static List<BlockPos> cells(BlockPos anchor, Direction facing) {
        List<BlockPos> out = new ArrayList<>(4);
        for (MarketBoardPart p : values()) out.add(BOTTOM_LEFT.posOf(p, anchor, facing));
        return out;
    }

    /** The side towards the part beside this one. */
    public Direction sideways(Direction facing) {
        return column == 0 ? right(facing) : right(facing).getOpposite();
    }

    /** The side towards the part above or below this one. */
    public Direction vertical() {
        return row == 0 ? Direction.UP : Direction.DOWN;
    }

    /** The part beside this one (same row). */
    public MarketBoardPart sidewaysPartner() {
        return at(1 - column, row);
    }

    /** The part above or below this one (same column). */
    public MarketBoardPart verticalPartner() {
        return at(column, 1 - row);
    }

    /** The partner this part expects on {@code side}, or null if {@code side} leads out of the board. */
    public MarketBoardPart partnerOn(Direction side, Direction facing) {
        if (side == sideways(facing)) return sidewaysPartner();
        if (side == vertical()) return verticalPartner();
        return null;
    }
}
