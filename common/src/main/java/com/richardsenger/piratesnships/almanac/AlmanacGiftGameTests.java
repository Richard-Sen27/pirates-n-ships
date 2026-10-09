package com.richardsenger.piratesnships.almanac;

import com.richardsenger.piratesnships.core.gametest.ConfigOverrides;
import com.richardsenger.piratesnships.core.gametest.ModGameTest;
import com.richardsenger.piratesnships.core.gametest.ModGameTests;
import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.Collection;

/**
 * The first-join Almanac (GUIDE2, {@link AlmanacGift}): the first login gives one, a second gives none, a player who
 * lost the book gets none, and {@code almanac.give_on_first_join} off gives none and leaves the flag unset. The login
 * hook is called directly with a mock player, which carries attachments like a real one.
 */
public final class AlmanacGiftGameTests {

    private AlmanacGiftGameTests() {
    }

    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        return ModGameTests.of(AlmanacGiftGameTests.class);
    }

    private static Player newPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static int almanacs(Player player) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(AlmanacContent.ALMANAC_ITEM.get())) n += stack.getCount();
        }
        return n;
    }

    @ModGameTest
    public static void firstJoinGivesOneAlmanacOnce(GameTestHelper helper) {
        ConfigOverrides.during(helper, AlmanacConfig.GIVE_ON_FIRST_JOIN, true);
        Player player = newPlayer(helper);

        helper.assertTrue(AlmanacGift.onLogin(player), "first login gives the Almanac");
        helper.assertValueEqual(almanacs(player), 1, "almanacs after the first login");
        helper.assertTrue(Services.ATTACHMENTS.get(player, AlmanacGift.GIVEN), "given flag after the first login");

        helper.assertFalse(AlmanacGift.onLogin(player), "second login gives nothing");
        helper.assertValueEqual(almanacs(player), 1, "almanacs after the second login");

        // the flag survives an emptied inventory: a player who threw the book away does not get another
        player.getInventory().clearContent();
        helper.assertFalse(AlmanacGift.onLogin(player), "login after losing the book gives nothing");
        helper.assertValueEqual(almanacs(player), 0, "no new book");
        helper.succeed();
    }

    @ModGameTest
    public static void noAlmanacWhenDisabled(GameTestHelper helper) {
        ConfigOverrides.during(helper, AlmanacConfig.GIVE_ON_FIRST_JOIN, false);
        Player player = newPlayer(helper);
        helper.assertFalse(AlmanacGift.onLogin(player), "no book while give_on_first_join is off");
        helper.assertValueEqual(almanacs(player), 0, "almanacs");
        helper.assertFalse(Services.ATTACHMENTS.get(player, AlmanacGift.GIVEN), "flag stays unset, so the book comes once enabled");
        helper.succeed();
    }
}
