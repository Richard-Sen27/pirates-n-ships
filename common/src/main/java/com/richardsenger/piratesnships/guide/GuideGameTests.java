package com.richardsenger.piratesnships.guide;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;

import java.util.Collection;
import java.util.Optional;

/**
 * The first-join guide book and its recipe in a running world. The GameTest server has GuideME on its classpath
 * (neoforge/build.gradle), so the tests check the real item and component; without GuideME they check that nothing is
 * given and the recipe is not loaded (its {@code neoforge:conditions}). The login hook is called directly with a mock
 * player, which carries attachments like a real one.
 */
public final class GuideGameTests {

    private GuideGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(GuideGameTests.class);
    }

    private static Player newPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static int guideBooks(Player player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (GuideBook.isGuideBook(stack)) n += stack.getCount();
        }
        return n;
    }

    @ModGameTest
    public static void firstJoinGivesOneGuideBookOnce(GameTestHelper helper) {
        ConfigOverrides.during(helper, GuideConfig.GIVE_ON_FIRST_JOIN, true);
        Player player = newPlayer(helper);
        boolean present = GuideBook.available();
        helper.assertValueEqual(present, Services.PLATFORM.isModLoaded(GuideBook.GUIDEME), "guide item registered iff GuideME is loaded");

        boolean given = GuideGift.onLogin(player);
        helper.assertValueEqual(given, present, "first login gives a book iff GuideME is installed");
        helper.assertValueEqual(guideBooks(player), present ? 1 : 0, "guide books after the first login");
        helper.assertValueEqual(Services.ATTACHMENTS.get(player, GuideGift.GIVEN), present, "given flag after the first login");

        helper.assertFalse(GuideGift.onLogin(player), "second login gives nothing");
        helper.assertValueEqual(guideBooks(player), present ? 1 : 0, "guide books after the second login");

        // the flag survives an emptied inventory: a player who threw the book away does not get another
        player.getInventory().clearContent();
        helper.assertFalse(GuideGift.onLogin(player), "login after losing the book gives nothing");
        helper.assertValueEqual(guideBooks(player), 0, "no new book");
        helper.succeed();
    }

    @ModGameTest
    public static void noGuideBookWhenDisabled(GameTestHelper helper) {
        ConfigOverrides.during(helper, GuideConfig.GIVE_ON_FIRST_JOIN, false);
        Player player = newPlayer(helper);
        helper.assertFalse(GuideGift.onLogin(player), "no book while give_on_first_join is off");
        helper.assertValueEqual(guideBooks(player), 0, "guide books");
        helper.assertFalse(Services.ATTACHMENTS.get(player, GuideGift.GIVEN), "flag stays unset, so the book comes once enabled");
        helper.succeed();
    }

    @ModGameTest
    public static void guideBookRecipeMakesTheBook(GameTestHelper helper) {
        Optional<RecipeHolder<?>> recipe = helper.getLevel().getRecipeManager().byKey(GuideModule.RECIPE_ID);
        if (!GuideBook.available()) {
            helper.assertFalse(recipe.isPresent(), "recipe must not load without GuideME");
            helper.succeed();
            return;
        }
        helper.assertTrue(recipe.isPresent(), "guide book recipe is loaded");
        ItemStack result = recipe.get().value().getResultItem(helper.getLevel().registryAccess());
        helper.assertTrue(GuideBook.isGuideBook(result), "recipe result is GuideME's guide item with our guide id, got " + result);
        helper.assertValueEqual(result.getCount(), 1, "result count");
        helper.succeed();
    }
}
