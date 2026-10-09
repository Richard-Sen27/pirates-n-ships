package com.richardsenger.piratesnships.compat.jei;

import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.compat.CompatText;
import com.richardsenger.piratesnships.compat.ProvisionEntry;
import com.richardsenger.piratesnships.crew.content.CrewContent;
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

/**
 * "Ship's Provisions" (JEI1): one page per item the crew counts as food, water or rum ({@link ProvisionEntry}), with
 * its value, shelf life and weight. Catalysts: the pantry and the water barrel, which hold them.
 */
final class ProvisionCategory extends AbstractRecipeCategory<ProvisionEntry> {

    static final RecipeType<ProvisionEntry> TYPE = RecipeType.create(Constants.MOD_ID, "ships_provisions", ProvisionEntry.class);

    static final int WIDTH = 160;
    private static final int TEXT_LEFT = 24;
    private static final int LINES = 4;

    ProvisionCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable(CompatText.CATEGORY_PROVISIONS), gui.createDrawableItemLike(CrewContent.PANTRY.get()),
                WIDTH, 2 + LINES * JeiText.LINE);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ProvisionEntry recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .setStandardSlotBackground()
                .addItemStack(recipe.stack());
    }

    @Override
    public void draw(ProvisionEntry recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        JeiText.draw(guiGraphics, Minecraft.getInstance().font, recipe.lines(), TEXT_LEFT, 2, WIDTH - TEXT_LEFT);
    }

    @Override
    public ResourceLocation getRegistryName(ProvisionEntry recipe) {
        return BuiltInRegistries.ITEM.getKey(recipe.stack().getItem());
    }
}
