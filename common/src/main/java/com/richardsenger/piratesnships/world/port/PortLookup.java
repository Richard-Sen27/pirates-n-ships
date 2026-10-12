package com.richardsenger.piratesnships.world.port;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Finds a port from what an operator typed (NAME3), pure: the full id ({@code pirates_n_ships:village_1192_1530}), the
 * id's path alone ({@code village_1192_1530}), the proper name ({@code "Port Marlow"}, case-insensitive) or the start of
 * a name when only one port's name starts so. Several ports for a name's start are {@link Result.Ambiguous}, and the
 * command lists them.
 */
public final class PortLookup {

    /** A port as the lookup sees it. */
    public record Entry(ResourceLocation id, String name) {
    }

    public sealed interface Result {
        record Found(ResourceLocation id) implements Result {
        }

        /** More than one port fits; their ids sorted by name. */
        record Ambiguous(List<Entry> candidates) implements Result {
        }

        record None() implements Result {
        }
    }

    private PortLookup() {
    }

    public static Result find(List<Entry> ports, String typed) {
        String query = unquote(typed);
        if (query.isEmpty()) return new Result.None();
        ResourceLocation asId = ResourceLocation.tryParse(query);
        if (asId != null) {
            for (Entry e : ports) if (e.id().equals(asId)) return new Result.Found(e.id());
        }
        String key = PortNames.key(query);
        List<Entry> paths = ports.stream().filter(e -> e.id().getPath().equalsIgnoreCase(query)).toList();
        if (!query.contains(":") && paths.size() == 1) return new Result.Found(paths.get(0).id());
        for (Entry e : ports) if (!e.name().isEmpty() && PortNames.key(e.name()).equals(key)) return new Result.Found(e.id());
        if (paths.size() > 1) return new Result.Ambiguous(sorted(paths));
        List<Entry> starts = new ArrayList<>();
        for (Entry e : ports) if (!e.name().isEmpty() && PortNames.key(e.name()).startsWith(key)) starts.add(e);
        if (starts.size() == 1) return new Result.Found(starts.get(0).id());
        if (starts.size() > 1) return new Result.Ambiguous(sorted(starts));
        return new Result.None();
    }

    /** The port of {@code id} or, for a {@code minecraft:} id (an unqualified word), of its path as typed. */
    public static Result find(List<Entry> ports, ResourceLocation typed) {
        Result r = find(ports, typed.toString());
        if (r instanceof Result.None && typed.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) return find(ports, typed.getPath());
        return r;
    }

    public static Optional<ResourceLocation> found(Result r) {
        return r instanceof Result.Found f ? Optional.of(f.id()) : Optional.empty();
    }

    private static List<Entry> sorted(List<Entry> list) {
        return list.stream().sorted(Comparator.comparing((Entry e) -> PortNames.key(e.name())).thenComparing(e -> e.id().toString())).toList();
    }

    static String unquote(String s) {
        String t = s.strip();
        if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) t = t.substring(1, t.length() - 1).strip();
        return t;
    }
}
