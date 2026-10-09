package com.richardsenger.piratesnships.compat.jei;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.compat.CompatText;
import com.richardsenger.piratesnships.compat.MarketEntry;
import com.richardsenger.piratesnships.trade.content.TradeContent;
import com.richardsenger.piratesnships.trade.desk.HarborDesks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * "Harbor Market" (JEI1): one page per trade good, the good on the left, the doubloon on the right, and below them the
 * prices at a settled port ({@link MarketEntry}). Catalyst: the harbor master's desk, where the market opens.
 */
final class MarketCategory extends AbstractRecipeCategory<MarketEntry> {

    static final RecipeType<MarketEntry> TYPE = RecipeType.create(Constants.MOD_ID, "harbor_market", MarketEntry.class);

    static final int WIDTH = 176;
    private static final int TEXT_TOP = 24;
    private static final int LINES = 6;

    private final IGuiHelper gui;

    MarketCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable(CompatText.CATEGORY_MARKET), gui.createDrawableItemLike(HarborDesks.HARBOR_DESK.get()),
                WIDTH, TEXT_TOP + LINES * JeiText.LINE);
        this.gui = gui;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MarketEntry recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 51, 2)
                .setStandardSlotBackground()
                .addItemStack(stack(recipe.item()));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 109, 2)
                .setOutputSlotBackground()
                .addItemStack(new ItemStack(TradeContent.DOUBLOON.get()));
    }

    @Override
    public void draw(MarketEntry recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        gui.getRecipeArrow().draw(guiGraphics, 76, 2);
        JeiText.draw(guiGraphics, Minecraft.getInstance().font, recipe.lines(), 0, TEXT_TOP, WIDTH);
    }

    @Override
    public ResourceLocation getRegistryName(MarketEntry recipe) {
        return recipe.good();
    }

    private static ItemStack stack(ResourceLocation item) {
        return new ItemStack(BuiltInRegistries.ITEM.get(item));
    }
}
