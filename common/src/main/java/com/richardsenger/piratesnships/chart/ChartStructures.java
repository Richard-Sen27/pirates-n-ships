package com.richardsenger.piratesnships.chart;

import com.richardsenger.piratesnships.chart.data.ChartSightings;
import com.richardsenger.piratesnships.chart.data.StructureKind;
import com.richardsenger.piratesnships.chart.data.StructureReveal;
import com.richardsenger.piratesnships.chart.net.ChartStructure;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.world.port.Port;
import com.richardsenger.piratesnships.world.port.PortRegistry;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Structures on the chart, server side (work package CHART2). On every chart sample ({@link ChartService#sampleNow})
 * the player's {@link ChartSightings} learn the ports and this mod's structures (wrecks and any later ones) whose
 * bounds lie within {@code chart.structure_reveal_distance}: ports from the {@link PortRegistry}, structures from the
 * starts referenced by the loaded chunks around the player. {@link #shown} turns the registry and the sightings into
 * what the chart draws ({@link StructureReveal}). Overworld only, like the chart.
 */
public final class ChartStructures {

    /**
     * A port's label on the chart: its name once the port registry has one (NAME3), else empty and the chart shows
     * the id's path. Replace the default when ports get names.
     */
    private static volatile Function<Port, Optional<String>> portName = p -> Optional.empty();

    private ChartStructures() {
    }

    public static ChartSightings sightings(Player player) {
        return Services.ATTACHMENTS.get(player, ChartAttachments.SIGHTINGS);
    }

    public static void setSightings(Player player, ChartSightings s) {
        Services.ATTACHMENTS.set(player, ChartAttachments.SIGHTINGS, s);
    }

    /** Installs how a port's name is read (NAME3 adds names to ports). */
    public static void setPortName(Function<Port, Optional<String>> f) {
        portName = f;
    }

    public static String label(Port p) {
        return portName.apply(p).filter(s -> !s.isBlank()).orElse(p.id().getPath());
    }

    /** Adds what is near the player now to their sightings; returns the (possibly unchanged) sightings. */
    public static ChartSightings reveal(ServerPlayer player) {
        ChartSightings known = sightings(player);
        if (!ChartConfig.SHOW_STRUCTURES.get() || !ChartService.charted(player.level())) return known;
        int distance = ChartConfig.STRUCTURE_REVEAL_DISTANCE.get();
        List<StructureReveal.Candidate> candidates = candidates(player.serverLevel(), player.getX(), player.getZ(), distance);
        ChartSightings next = StructureReveal.reveal(known, candidates, player.getX(), player.getZ(), distance);
        if (next != known) setSightings(player, next);
        return next;
    }

    /**
     * The ports and this mod's structures whose bounds may lie within {@code distance} of {@code (x, z)}: every port of
     * the level, and every structure start referenced by a loaded chunk within that distance (a structure's
     * references cover every chunk its bounds touch). Vanilla structures are left out.
     */
    public static List<StructureReveal.Candidate> candidates(ServerLevel level, double x, double z, int distance) {
        List<StructureReveal.Candidate> out = new ArrayList<>();
        for (Port p : PortRegistry.get(level.getServer()).index().all()) {
            if (!p.dimension().equals(level.dimension())) continue;
            BoundingBox b = p.box();
            out.add(new StructureReveal.Candidate(p.id(), true, b.minX(), b.minZ(), b.maxX(), b.maxZ()));
        }
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        int cx = Math.floorDiv((int) Math.floor(x), 16);
        int cz = Math.floorDiv((int) Math.floor(z), 16);
        int r = Math.floorDiv(distance + 15, 16);
        Set<String> seen = new HashSet<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (Map.Entry<Structure, LongSet> e : chunk.getAllReferences().entrySet()) {
                    ResourceLocation id = registry.getKey(e.getKey());
                    if (id == null) continue;
                    StructureKind kind = StructureKind.ofStructure(id);
                    if (kind == null || kind.port()) continue;
                    for (LongIterator it = e.getValue().iterator(); it.hasNext(); ) {
                        long startChunk = it.nextLong();
                        if (!seen.add(id + "@" + startChunk)) continue;
                        LevelChunk home = level.getChunkSource().getChunkNow(ChunkPos.getX(startChunk), ChunkPos.getZ(startChunk));
                        if (home == null) continue;
                        StructureStart start = home.getStartForStructure(e.getKey());
                        if (start == null || !start.isValid()) continue;
                        BoundingBox b = start.getBoundingBox();
                        out.add(new StructureReveal.Candidate(id, false, b.minX(), b.minZ(), b.maxX(), b.maxZ()));
                    }
                }
            }
        }
        return out;
    }

    /** What the chart of {@code player} draws now; empty with {@code chart.show_structures} off or outside the overworld. */
    public static List<ChartStructure> shown(ServerPlayer player) {
        if (!ChartConfig.SHOW_STRUCTURES.get() || !ChartService.charted(player.level())) return List.of();
        List<StructureReveal.PortEntry> ports = new ArrayList<>();
        for (Port p : PortRegistry.get(player.server).index().all()) {
            if (!p.dimension().equals(player.level().dimension())) continue;
            ports.add(new StructureReveal.PortEntry(p.id(), StructureKind.of(p.kind()), p.centre().getX(), p.centre().getZ(), label(p)));
        }
        return StructureReveal.shown(ports, sightings(player), ChartConfig.PORTS_ALWAYS_KNOWN.get(), player.getX(), player.getZ());
    }
}
