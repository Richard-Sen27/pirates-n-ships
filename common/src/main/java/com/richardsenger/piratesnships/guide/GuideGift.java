package com.richardsenger.piratesnships.guide;

import com.mojang.serialization.Codec;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.attachment.AttachmentKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Gives a player the guide book once, on their first join (server config {@code guide.give_on_first_join}). The
 * "already given" flag is a persistent player attachment that survives death. It is only set when a book was
 * actually given: without GuideME nobody gets one, and a player who joined before GuideME was installed gets theirs
 * at the next login after it is.
 */
public final class GuideGift {

    /** Whether this player has been given the guide book. */
    public static final AttachmentKey<Boolean> GIVEN = AttachmentKey.builder("guide_book_given", () -> false)
            .persistent(Codec.BOOL)
            .copyOnDeath()
            .build();

    private GuideGift() {
    }

    /** Called from {@code GuideModule.registerContent()}. */
    public static void init() {
        Services.ATTACHMENTS.register(GIVEN);
    }

    /** Login hook (server side). Returns whether a book was given. */
    public static boolean onLogin(Player player) {
        if (player.level().isClientSide() || !GuideConfig.GIVE_ON_FIRST_JOIN.get()) return false;
        if (Services.ATTACHMENTS.get(player, GIVEN)) return false;
        Optional<ItemStack> book = GuideBook.create(player.level().registryAccess());
        if (book.isEmpty()) return false;
        ItemStack stack = book.get();
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        Services.ATTACHMENTS.set(player, GIVEN, true);
        return true;
    }
}
