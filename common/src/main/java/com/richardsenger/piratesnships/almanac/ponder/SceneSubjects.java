package com.richardsenger.piratesnships.almanac.ponder;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.ship.decor.wood.WoodFamily;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What a scene's subjects ({@link AlmanacScenes.Scene#subjects}) stand for in a running game (PND5): every member of a
 * subject's wood family ({@link WoodFamily#ofPath}: {@code oak_railing} gives every railing of every shipped wood,
 * {@code helm} every helm), and only ids that are items (a block without an item, like the railing latch, can carry no
 * inventory hint). No Ponder class: the GameTests check it on the server.
 */
public final class SceneSubjects {

    private SceneSubjects() {
    }

    /** The registered resolver: wood families from {@link WoodFamily}, items from the item registry. */
    public static Function<ResourceLocation, List<ResourceLocation>> registered() {
        return resolver(SceneSubjects::familyOf, BuiltInRegistries.ITEM::containsKey);
    }

    /**
     * A resolver from a family lookup and an item test: a subject stands for its family's members (or itself), those
     * that are items.
     */
    public static Function<ResourceLocation, List<ResourceLocation>> resolver(
            Function<ResourceLocation, List<ResourceLocation>> family, Predicate<ResourceLocation> isItem) {
        return id -> family.apply(id).stream().filter(isItem).toList();
    }

    /** Every member of {@code id}'s wood family, or {@code id} alone if it is in none (or not ours). */
    public static List<ResourceLocation> familyOf(ResourceLocation id) {
        if (!Constants.MOD_ID.equals(id.getNamespace())) {
            return List.of(id);
        }
        WoodFamily<?> family = WoodFamily.ofPath(id.getPath());
        return family == null ? List.of(id) : family.ids();
    }

    /**
     * The ids Ponder's index leaves out: every wood family member but the family's face ({@link WoodFamily#lead}), so
     * the index shows one railing, not one per wood, while the hint shows on all of them.
     */
    public static Set<ResourceLocation> hiddenFromIndex() {
        Set<ResourceLocation> out = new HashSet<>();
        for (WoodFamily<?> family : WoodFamily.all()) {
            List<ResourceLocation> ids = new ArrayList<>(family.ids());
            ids.remove(family.lead().id());
            out.addAll(ids);
        }
        return out;
    }
}
