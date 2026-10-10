package com.richardsenger.piratesnships.world.structure;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The shape of one generated jigsaw structure, for the natural-layout checks (STR2, reused by STR3 and STR4): its
 * pieces in jigsaw order (name and bounding box), the whole bounding box, the aspect of its footprint, the distinct
 * building variants and the text dump {@code tools/structure_check.py} reads. Pure: no world access.
 *
 * <p>The text dump ({@link #lines}): a header of {@code key value} lines ({@code structure}, {@code seed},
 * {@code pieces}, {@code bounds}, {@code size}, {@code aspect}, {@code buildings} (the pool's building names),
 * {@code variants}, {@code water_end}, {@code sequence}), then one {@code piece <index> <name> <minX> <minY> <minZ>
 * <maxX> <maxY> <maxZ>} line per piece.
 */
public record StructureLayout(List<Piece> pieces) {

    /** One placed piece: its template's name (the path after the group, e.g. {@code tent}) and its box. */
    public record Piece(String name, BoundingBox box) {
    }

    private static final Pattern TEMPLATE = Pattern.compile("[a-z0-9_.-]+:(?:[a-z0-9_.-]+/)*([a-z0-9_.-]+)\\]");

    public StructureLayout {
        pieces = List.copyOf(pieces);
    }

    /** The layout of jigsaw pieces; a piece that is not a pool element is named {@code ?}. */
    public static StructureLayout of(List<StructurePiece> pieces) {
        List<Piece> out = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            String name = piece instanceof PoolElementStructurePiece p ? templateName(p.getElement().toString()) : "?";
            out.add(new Piece(name, piece.getBoundingBox()));
        }
        return new StructureLayout(out);
    }

    /**
     * The template's last path segment in a pool element's description ({@code Single[Left[ns:group/tent]]} ->
     * {@code tent}), or {@code ?}.
     */
    public static String templateName(String element) {
        Matcher m = TEMPLATE.matcher(element);
        return m.find() ? m.group(1) : "?";
    }

    /** The box around every piece. */
    public BoundingBox bounds() {
        if (pieces.isEmpty()) throw new IllegalStateException("no pieces");
        BoundingBox box = pieces.get(0).box();
        int minX = box.minX(), minY = box.minY(), minZ = box.minZ(), maxX = box.maxX(), maxY = box.maxY(), maxZ = box.maxZ();
        for (Piece piece : pieces) {
            BoundingBox b = piece.box();
            minX = Math.min(minX, b.minX());
            minY = Math.min(minY, b.minY());
            minZ = Math.min(minZ, b.minZ());
            maxX = Math.max(maxX, b.maxX());
            maxY = Math.max(maxY, b.maxY());
            maxZ = Math.max(maxZ, b.maxZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Footprint aspect of {@link #bounds}: the shorter horizontal side over the longer one (1 is square, a line is near 0). */
    public double aspect() {
        BoundingBox b = bounds();
        int w = b.getXSpan();
        int d = b.getZSpan();
        return (double) Math.min(w, d) / Math.max(w, d);
    }

    /** The names among {@code buildings} that occur in this layout, sorted. */
    public Set<String> variants(Collection<String> buildings) {
        Set<String> out = new TreeSet<>();
        for (Piece piece : pieces) {
            if (buildings.contains(piece.name())) out.add(piece.name());
        }
        return out;
    }

    /** The pieces' names in jigsaw order, comma separated: two layouts with the same sequence have the same shape class. */
    public String sequence() {
        StringBuilder out = new StringBuilder();
        for (Piece piece : pieces) {
            if (!out.isEmpty()) out.append(',');
            out.append(piece.name());
        }
        return out.toString();
    }

    /** The pieces with this name. */
    public List<Piece> named(String name) {
        return pieces.stream().filter(p -> p.name().equals(name)).toList();
    }

    /** The text dump (see the class comment). */
    public List<String> lines(String structure, long seed, Collection<String> buildings, boolean waterEnd) {
        BoundingBox b = bounds();
        List<String> out = new ArrayList<>();
        out.add("structure " + structure);
        out.add("seed " + seed);
        out.add("pieces " + pieces.size());
        out.add("bounds " + b.minX() + " " + b.minY() + " " + b.minZ() + " " + b.maxX() + " " + b.maxY() + " " + b.maxZ());
        out.add("size " + b.getXSpan() + " " + b.getZSpan());
        out.add(String.format(java.util.Locale.ROOT, "aspect %.3f", aspect()));
        out.add("buildings " + String.join(",", new TreeSet<>(buildings)));
        out.add("variants " + variants(buildings).size() + " " + String.join(",", variants(buildings)));
        out.add("water_end " + waterEnd);
        out.add("sequence " + sequence());
        for (int i = 0; i < pieces.size(); i++) {
            BoundingBox p = pieces.get(i).box();
            out.add("piece " + i + " " + pieces.get(i).name() + " " + p.minX() + " " + p.minY() + " " + p.minZ() + " "
                    + p.maxX() + " " + p.maxY() + " " + p.maxZ());
        }
        return out;
    }
}
