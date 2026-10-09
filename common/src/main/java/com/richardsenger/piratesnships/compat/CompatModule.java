package com.richardsenger.piratesnships.compat;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;

import java.util.List;

/**
 * The {@code compat} module (JEI1, docs/compat.md): the loader-independent half of the recipe-viewer compat. It holds
 * the pure data the JEI plugin shows ({@link InfoPages}, {@link MarketEntry}, {@link ProvisionEntry},
 * {@link HiddenItems}), its lang and its GameTests. The plugin itself ({@code compat.jei.PiratesJeiPlugin}) is found by
 * JEI (its {@code @JeiPlugin} scan on NeoForge, the {@code jei_mod_plugin} entry point on Fabric); nothing in the mod
 * references the {@code compat.jei} package, so JEI's classes are loaded only when JEI is installed.
 *
 * <p>No config toggle: a recipe viewer's pages change no gameplay.
 */
public final class CompatModule implements ModModule {

    @Override
    public String id() {
        return "compat";
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> CompatText.lang().forEach(lang::add));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(CompatGameTests.class);
    }
}
