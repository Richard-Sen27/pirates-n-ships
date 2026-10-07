package com.richardsenger.piratesnships.ship.assembly;

import com.richardsenger.piratesnships.Constants;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What a helm action did. {@link #message()} is the translatable feedback for the player.
 *
 * @param shipId the assembled ship (only for {@link Outcome#ASSEMBLED} and {@link Outcome#ADOPTED})
 * @param where  the first obstructed position (only for {@link Outcome#OBSTRUCTED})
 * @param count  blocks moved, or the block limit for {@link Outcome#TOO_MANY_BLOCKS}
 */
public record AssemblyResult(Outcome outcome, int count, @Nullable UUID shipId, @Nullable BlockPos where, double value) {

    public enum Outcome {
        ASSEMBLED(true), DISASSEMBLED(true), NAMED(true), ADOPTED(true),
        DISABLED(false), TOO_MANY_BLOCKS(false), NOTHING_TO_ASSEMBLE(false), MOVING(false), NOT_LEVEL(false),
        OBSTRUCTED(false), OUT_OF_WORLD(false), NO_SHIP(false), FAILED(false);

        public final boolean success;

        Outcome(boolean success) {
            this.success = success;
        }

        public String key() {
            return Constants.MOD_ID + ".assembly." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static AssemblyResult of(Outcome outcome) {
        return new AssemblyResult(outcome, 0, null, null, 0);
    }

    public boolean success() {
        return outcome.success;
    }

    public Component message() {
        Object[] args = switch (outcome) {
            case ASSEMBLED, DISASSEMBLED, ADOPTED, TOO_MANY_BLOCKS -> new Object[] {count};
            case MOVING -> new Object[] {String.format(java.util.Locale.ROOT, "%.2f", value)};
            case NOT_LEVEL -> new Object[] {String.format(java.util.Locale.ROOT, "%.1f", value),
                    String.format(java.util.Locale.ROOT, "%.1f", AssemblyConfig.MAX_TILT_DEGREES.get())};
            case OBSTRUCTED, OUT_OF_WORLD -> where == null ? new Object[] {"?", "?", "?"}
                    : new Object[] {where.getX(), where.getY(), where.getZ()};
            default -> new Object[0];
        };
        return Component.translatable(outcome.key(), args).withStyle(success() ? ChatFormatting.GREEN : ChatFormatting.RED);
    }
}
