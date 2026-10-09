package com.richardsenger.piratesnships.core.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The action-bar line of a player action (MSG1, docs/messages.md). A routine success says nothing: its result is
 * {@link Component#empty()} (or null), and showing it here sends nothing, so the line already on the bar is not wiped.
 * Kept lines are short refusals or state the player cannot see; ship handling never uses the chat.
 */
public final class ActionBar {

    private ActionBar() {
    }

    /** Whether {@code message} says nothing (null, or an empty literal without siblings). */
    public static boolean silent(@Nullable Component message) {
        return message == null || message.getContents() == PlainTextContents.EMPTY && message.getSiblings().isEmpty();
    }

    /** Shows {@code message} on {@code player}'s action bar unless it is {@link #silent}. */
    public static void show(Player player, @Nullable Component message) {
        if (!silent(message)) {
            player.displayClientMessage(message, true);
        }
    }
}
