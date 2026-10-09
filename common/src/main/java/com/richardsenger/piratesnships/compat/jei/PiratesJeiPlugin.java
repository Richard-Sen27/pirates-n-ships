package com.richardsenger.piratesnships.compat.jei;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.almanac.data.Chapter;
import com.richardsenger.piratesnships.almanac.sync.ClientAlmanac;
import com.richardsenger.piratesnships.compat.CompatText;
import com.richardsenger.piratesnships.compat.HiddenItems;
import com.richardsenger.piratesnships.compat.InfoPages;
import com.richardsenger.piratesnships.compat.MarketEntry;
import com.richardsenger.piratesnships.compat.ProvisionEntry;
import com.richardsenger.piratesnships.core.data.Definitions;
import com.richardsenger.piratesnships.crew.content.CrewContent;
import com.richardsenger.piratesnships.crew.provisions.ProvisionsConfig;
import com.richardsenger.piratesnships.trade.TradeConfig;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import com.richardsenger.piratesnships.trade.good.TradeGood;
import com.richardsenger.piratesnships.trade.good.TradeGoods;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Our JEI plugin (JEI1, docs/compat.md, design.md §3.4). Found by JEI itself: the {@link JeiPlugin} annotation scan on
 * NeoForge, the {@code jei_mod_plugin} entry point in {@code fabric.mod.json} on Fabric. Nothing else in the mod
 * references this package, so without JEI none of its classes is ever loaded. It registers:
 * <ul>
 *   <li>an information page for every item of ours the Captain's Almanac describes ({@link InfoPages});</li>
 *   <li>the "Harbor Market" category (every trade good with its prices, catalyst: the harbor master's desk) and the
 *       "Ship's Provisions" category (what counts as food, water or rum aboard, catalysts: pantry and water
 *       barrel);</li>
 *   <li>the hidden items ({@link HiddenItems}), removed from the item list once the runtime is up.</li>
 * </ul>
 * The trade goods come from the server's sync; JEI may start before it arrives, so the shipped defaults stand in and
 * the page set is swapped when the sync comes ({@link #onTradeGoodsSync}).
 */
@JeiPlugin
public final class PiratesJeiPlugin implements IModPlugin {

    public static final ResourceLocation ID = Constants.id("jei_plugin");

    @Nullable
    private static IJeiRuntime runtime;
    private static List<MarketEntry> shownMarket = List.of();
    private static boolean syncListener;

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new MarketCategory(gui), new ProvisionCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        shownMarket = marketEntries(TradeGoods.TYPE.client());
        registration.addRecipes(MarketCategory.TYPE, shownMarket);
        registration.addRecipes(ProvisionCategory.TYPE, ProvisionEntry.all(ProvisionsConfig.settings()));
        registerInfoPages(registration);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(HarborDesks.HARBOR_DESK.get(), MarketCategory.TYPE);
        registration.addRecipeCatalyst(CrewContent.PANTRY.get(), ProvisionCategory.TYPE);
        registration.addRecipeCatalyst(CrewContent.WATER_BARREL.get(), ProvisionCategory.TYPE);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        List<ItemStack> hidden = new ArrayList<>();
        for (ResourceLocation id : HiddenItems.IDS) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> hidden.add(new ItemStack(item)));
        }
        if (!hidden.isEmpty()) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
        }
        if (!syncListener) {
            syncListener = true; // the client thread, once per game; listeners cannot be removed
            TradeGoods.TYPE.onClientSync(PiratesJeiPlugin::onTradeGoodsSync);
        }
        Constants.LOG.info("JEI plugin {}: {} market pages, {} hidden items", ID, shownMarket.size(), hidden.size());
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** The server's trade goods arrived (join or /reload): show its goods instead of the ones shown so far. */
    private static void onTradeGoodsSync(Definitions<TradeGood> goods) {
        IJeiRuntime rt = runtime;
        if (rt == null) return; // JEI not up yet: registerRecipes reads the client store when it starts
        List<MarketEntry> fresh = marketEntries(goods);
        if (fresh.equals(shownMarket)) return;
        if (!shownMarket.isEmpty()) rt.getRecipeManager().hideRecipes(MarketCategory.TYPE, shownMarket);
        rt.getRecipeManager().addRecipes(MarketCategory.TYPE, fresh);
        shownMarket = fresh;
    }

    /** The synced goods, or the shipped defaults when no sync has arrived yet. */
    private static List<MarketEntry> marketEntries(Definitions<TradeGood> synced) {
        Map<ResourceLocation, TradeGood> goods = synced.all().isEmpty() ? TradeGoods.DEFAULTS : synced.all();
        return MarketEntry.all(goods, TradeConfig.marketParams(), BuiltInRegistries.ITEM::containsKey);
    }

    private static void registerInfoPages(IRecipeRegistration registration) {
        Map<String, String> lang = AlmanacLangReader.read();
        if (lang.isEmpty()) return; // no lang loaded: no pages, nothing else changes
        Map<ResourceLocation, String> names = new LinkedHashMap<>();
        Map<ResourceLocation, Item> items = new LinkedHashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(Constants.MOD_ID) || HiddenItems.hidden(id)) continue;
            items.put(id, item);
            names.put(id, lang.getOrDefault(item.getDescriptionId(), ""));
        }
        // The synced book's chapter order, when the sync came before JEI started; else chapters alphabetically
        List<String> order = ClientAlmanac.book().chapters().stream().map(Chapter::id)
                .filter(c -> c.getNamespace().equals(Constants.MOD_ID)).map(c -> c.getPath().replace('/', '.')).toList();
        InfoPages.build(lang, names, order).forEach((id, page) -> registration.addIngredientInfo(items.get(id),
                Component.literal(page.text()),
                Component.translatable(CompatText.INFO_SOURCE, page.chapterTitle())));
    }
}
