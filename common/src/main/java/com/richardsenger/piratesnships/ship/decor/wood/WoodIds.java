package com.richardsenger.piratesnships.ship.decor.wood;

import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Family-aware id rules (WOOD1, docs/woods.md), pure (works on paths, no registry): which family a registry path of
 * ours belongs to and which member stands for the family in the Almanac (one recipe panel, one inventory line per
 * family) and in recipe viewers' information pages.
 */
public final class WoodIds {

    private WoodIds() {
    }

    /**
     * The family suffix of {@code path} if it is {@code <wood>_<suffix>} for a known wood ({@link ShipWood#all()}, the
     * longest matching wood id), else {@code null}: {@code dark_oak_railing} gives {@code railing}.
     */
    @Nullable
    public static String suffixOf(String path) {
        String best = null;
        for (ShipWood w : ShipWood.all()) {
            String prefix = w.id() + "_";
            if (path.startsWith(prefix) && path.length() > prefix.length() && (best == null || prefix.length() > best.length() + 1)) {
                best = w.id();
            }
        }
        return best == null ? null : path.substring(best.length() + 1);
    }

    /**
     * The member that stands for {@code path}'s family among the ids {@code exists} knows: the family's legacy id
     * ({@code <suffix>}) if it exists, else oak's ({@code oak_<suffix>}); {@code path} itself when it is no family
     * member (or the family's lead does not exist).
     */
    public static String representative(String path, Predicate<String> exists) {
        String suffix = suffixOf(path);
        if (suffix == null) return path;
        if (exists.test(suffix)) return suffix;
        String oak = WoodFamily.name(ShipWood.OAK, suffix);
        return exists.test(oak) ? oak : path;
    }
}
