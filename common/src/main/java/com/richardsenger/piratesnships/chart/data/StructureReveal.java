package com.richardsenger.piratesnships.chart.data;

import com.richardsenger.piratesnships.chart.net.ChartStructure;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which structures a chart shows (work package CHART2), pure. A structure is <b>revealed</b> when the player comes
 * within {@code chart.structure_reveal_distance} blocks of its bounds (horizontal distance from the player to the
 * nearest point of the box, 0 inside it) and stays on the chart for good. Ports are always shown with
 * {@code chart.ports_always_known}, otherwise only once revealed; wrecks and the other structures of this mod only once
 * revealed; vanilla structures never.
 */
public final class StructureReveal {

    /** Most structures one state payload carries; the nearest win. */
    public static final int MAX_SHOWN = 2048;

    /**
     * A structure near the player this sample: a port ({@code port} true, {@code id} its port id) or a structure of
     * this mod ({@code id} its registry id), with its bounds in blocks (x and z, inclusive).
     */
    public record Candidate(ResourceLocation id, boolean port, int minX, int minZ, int maxX, int maxZ) {
        int centreX() {
            return Math.floorDiv(minX + maxX, 2);
        }

        int centreZ() {
            return Math.floorDiv(minZ + maxZ, 2);
        }
    }

    /** A registered port as the chart needs it. */
    public record PortEntry(ResourceLocation id, StructureKind kind, int x, int z, String label) {
    }

    private StructureReveal() {
    }

    /** Horizontal distance from {@code (x, z)} to the box {@code [minX, maxX] x [minZ, maxZ]}, 0 inside it. */
    public static double distanceToBox(double x, double z, int minX, int minZ, int maxX, int maxZ) {
        double dx = x < minX ? minX - x : x > maxX + 1 ? x - (maxX + 1) : 0;
        double dz = z < minZ ? minZ - z : z > maxZ + 1 ? z - (maxZ + 1) : 0;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean near(double x, double z, Candidate c, double distance) {
        return distanceToBox(x, z, c.minX(), c.minZ(), c.maxX(), c.maxZ()) <= distance;
    }

    /**
     * The sightings after a player stood at {@code (x, z)}: every candidate within {@code distance} that is not known
     * yet is added (a port by its id, a structure by its id and centre; a port-kind structure start is left to the
     * port registry). The same instance when nothing is new.
     */
    public static ChartSightings reveal(ChartSightings known, List<Candidate> candidates, double x, double z, double distance) {
        List<ResourceLocation> ports = null;
        List<KnownStructure> structures = null;
        for (Candidate c : candidates) {
            if (!near(x, z, c, distance)) continue;
            if (c.port()) {
                if (known.knowsPort(c.id()) || ports != null && ports.contains(c.id())) continue;
                if (ports == null) ports = new ArrayList<>(known.ports());
                ports.add(c.id());
            } else {
                StructureKind kind = StructureKind.ofStructure(c.id());
                if (kind == null || kind.port()) continue;
                KnownStructure s = new KnownStructure(c.id(), c.centreX(), c.centreZ());
                if (known.knows(s) || structures != null && structures.contains(s)) continue;
                if (structures == null) structures = new ArrayList<>(known.structures());
                structures.add(s);
            }
        }
        if (ports == null && structures == null) return known;
        return new ChartSightings(ports == null ? known.ports() : ports, structures == null ? known.structures() : structures);
    }

    /**
     * What the chart draws: the ports (all with {@code portsAlwaysKnown}, else the revealed ones), then the revealed
     * wrecks and other structures (labelled with their structure id's path, a wreck without a label), at most
     * {@link #MAX_SHOWN}, nearest to {@code (x, z)} first.
     */
    public static List<ChartStructure> shown(List<PortEntry> ports, ChartSightings known, boolean portsAlwaysKnown, double x, double z) {
        List<ChartStructure> out = new ArrayList<>();
        Set<ResourceLocation> seen = new LinkedHashSet<>();
        for (PortEntry p : ports) {
            if (!seen.add(p.id())) continue;
            if (portsAlwaysKnown || known.knowsPort(p.id())) out.add(new ChartStructure(p.kind(), p.x(), p.z(), p.label()));
        }
        for (KnownStructure s : known.structures()) {
            StructureKind kind = StructureKind.ofStructure(s.structure());
            if (kind == null || kind.port()) continue;
            out.add(new ChartStructure(kind, s.x(), s.z(), kind == StructureKind.WRECK ? "" : s.structure().getPath()));
        }
        if (out.size() > MAX_SHOWN) {
            out.sort(Comparator.comparingDouble(c -> sq(c.x() - x) + sq(c.z() - z)));
            return List.copyOf(out.subList(0, MAX_SHOWN));
        }
        return out;
    }

    private static double sq(double d) {
        return d * d;
    }
}
