package com.richardsenger.piratesnships.almanac;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Gives a player one Captain's Almanac on their first join (server config {@code almanac.give_on_first_join},
 * GUIDE2; it replaced the old guide book gift). The "already given" flag is a persistent player attachment that
 * survives death, so a player who throws the book away does not get another. It is only set when a book was given:
 * while the toggle is off nothing is given or stored, and the book comes at the next login after it is turned on.
 * <p>
 * The flag is a new attachment ({@code almanac_given}), not the old {@code guide_book_given}: a player who got the
 * old guide book before GUIDE2 gets the Almanac once too.
 */
public final class AlmanacGift {

    /** Whether this player has been given the Almanac. */
    public static final AttachmentKey<Boolean> GIVEN = AttachmentKey.builder("almanac_given", () -> false)
            .persistent(Codec.BOOL)
            .copyOnDeath()
            .build();

    private AlmanacGift() {
    }

    /** Called from {@code AlmanacModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(GIVEN);
    }

    /** Login hook (server side). Returns whether a book was given. */
    public static boolean onLogin(Player player) {
        if (player.level().isClientSide() || !AlmanacConfig.GIVE_ON_FIRST_JOIN.get()) return false;
        if (Services.ATTACHMENTS.get(player, GIVEN)) return false;
        ItemStack stack = new ItemStack(AlmanacContent.ALMANAC_ITEM.get());
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        Services.ATTACHMENTS.set(player, GIVEN, true);
        return true;
    }
}
