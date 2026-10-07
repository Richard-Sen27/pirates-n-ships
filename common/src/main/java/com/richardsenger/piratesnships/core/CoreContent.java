package com.richardsenger.piratesnships.core;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.registry.ModRegistry;
import com.richardsenger.piratesnships.platform.registry.RegistryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Objects;
import java.util.function.Supplier;

/** Registry content of the {@code core} module. */
public final class CoreContent {

    /** Icon of {@link #TAB} until a feature module sets one: a vanilla item, so core needs no feature module. */
    private static final Supplier<ItemStack> FALLBACK_TAB_ICON = () -> new ItemStack(Items.OAK_BOAT);

    private static volatile Supplier<ItemStack> tabIcon = FALLBACK_TAB_ICON;

    /**
     * The mod's creative tab. Lists every item registered through {@link ModRegistry}. Its icon comes from
     * {@link #setTabIcon}; vanilla reads it lazily (on the first {@link CreativeModeTab#getIconItem()}), long after
     * every module has registered its content.
     */
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> TAB = ModRegistry.creativeTab("main",
            () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup." + Constants.MOD_ID))
                    .icon(() -> tabIcon.get())
                    .displayItems((params, output) -> ModRegistry.items().forEach(item -> output.accept(item.get())))
                    .build());

    private CoreContent() {
    }

    public static void init() {
    }

    /**
     * Sets the icon of {@link #TAB} without core knowing the feature module that owns the item. Call from a module's
     * {@code registerContent()}; the supplier is evaluated lazily, once registries are frozen. The apparel module
     * sets the officer's bicorne.
     */
    public static void setTabIcon(Supplier<ItemStack> icon) {
        tabIcon = Objects.requireNonNull(icon, "icon");
    }
}
