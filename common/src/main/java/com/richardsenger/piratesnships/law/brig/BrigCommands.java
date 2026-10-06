package com.richardsenger.piratesnships.law.brig;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.law.bounty.PirateTier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Operator debug commands {@code /pirates brig ...} (permission 2) to try the outcomes before navy officers exist.
 * The executing player is the claimant. Doubloons are only printed (no coins yet).
 */
public final class BrigCommands {

    private static final SimpleCommandExceptionType NOT_LIVING = new SimpleCommandExceptionType(Component.literal("Target is not a living entity"));

    private BrigCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("brig")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(c -> list(c, 64))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 512)).executes(c -> list(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("capture").then(Commands.argument("target", EntityArgument.entity()).executes(BrigCommands::capture)))
                .then(Commands.literal("deliver").then(Commands.argument("prisoner", EntityArgument.entity())
                        .executes(c -> deliver(c, null))
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(PirateTier.values()).map(PirateTier::id), b))
                                .executes(c -> deliver(c, StringArgumentType.getString(c, "tier"))))))
                .then(Commands.literal("ransom").then(Commands.argument("prisoner", EntityArgument.entity())
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RansomRules.Kind.values()).map(RansomRules.Kind::id), b))
                                .executes(c -> ransom(c, false))
                                .then(Commands.argument("captain", BoolArgumentType.bool()).executes(c -> ransom(c, BoolArgumentType.getBool(c, "captain")))))))
                .then(Commands.literal("pressgang").then(Commands.argument("prisoner", EntityArgument.entity()).executes(BrigCommands::pressGang)))
                .then(Commands.literal("release").then(Commands.argument("prisoner", EntityArgument.entity()).executes(BrigCommands::release)))));
    }

    private static LivingEntity living(CommandContext<CommandSourceStack> c, String arg) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, arg);
        if (e instanceof LivingEntity l) return l;
        throw NOT_LIVING.create();
    }

    private static int list(CommandContext<CommandSourceStack> c, int radius) {
        CommandSourceStack s = c.getSource();
        List<LivingEntity> found = BrigService.prisonersWithin(s.getLevel(), new AABB(s.getPosition(), s.getPosition()).inflate(radius));
        s.sendSuccess(() -> Component.literal(found.size() + " prisoner(s) within " + radius + " blocks"), false);
        for (LivingEntity e : found) {
            PrisonerState st = BrigService.state(e);
            boolean cell = BrigService.isInCell(e);
            s.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "- %s (%s) at %d %d %d, captor %s, %s%s",
                    e.getName().getString(), e.getStringUUID(), e.getBlockX(), e.getBlockY(), e.getBlockZ(), st.captorName(),
                    st.led() ? "led" : "not led", cell ? ", in a locked cell" : "")), false);
        }
        return found.size();
    }

    private static int capture(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        LivingEntity target = living(c, "target");
        CaptureRules.Result r = BrigService.capture(player, target);
        c.getSource().sendSuccess(() -> Component.literal("Capture: " + r.name().toLowerCase(Locale.ROOT)), false);
        return r.ok() ? 1 : 0;
    }

    private static int deliver(CommandContext<CommandSourceStack> c, String tierId) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        LivingEntity prisoner = living(c, "prisoner");
        PirateTier tier = tierId == null ? null : Arrays.stream(PirateTier.values()).filter(t -> t.id().equals(tierId)).findFirst().orElse(null);
        if (tierId != null && tier == null) {
            c.getSource().sendFailure(Component.literal("Unknown tier " + tierId));
            return 0;
        }
        PrisonerOutcomes.DeliverResult r = PrisonerOutcomes.deliver(prisoner, player, tier);
        c.getSource().sendSuccess(() -> Component.literal(r.success() ? "Delivered, payout " + r.payout() + " doubloons"
                : "Not delivered: " + r.failure().name().toLowerCase(Locale.ROOT)), false);
        return r.payout();
    }

    private static int ransom(CommandContext<CommandSourceStack> c, boolean captain) throws CommandSyntaxException {
        LivingEntity prisoner = living(c, "prisoner");
        String kindId = StringArgumentType.getString(c, "kind");
        RansomRules.Kind kind = Arrays.stream(RansomRules.Kind.values()).filter(k -> k.id().equals(kindId)).findFirst().orElse(null);
        if (kind == null) {
            c.getSource().sendFailure(Component.literal("Unknown kind " + kindId));
            return 0;
        }
        PrisonerOutcomes.RansomResult r = PrisonerOutcomes.ransom(prisoner, kind, captain);
        c.getSource().sendSuccess(() -> Component.literal(r.success() ? "Ransomed for " + r.amount() + " doubloons"
                : "Not ransomed: " + r.failure().name().toLowerCase(Locale.ROOT)), false);
        return r.amount();
    }

    private static int pressGang(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        PrisonerOutcomes.PressGangResult r = PrisonerOutcomes.pressGang(living(c, "prisoner"), player);
        c.getSource().sendSuccess(() -> Component.literal(r.success()
                ? "Press-ganged " + r.recruit().name() + " (morale " + r.recruit().morale() + "), crime " + r.recruit().crime().outcome().name().toLowerCase(Locale.ROOT)
                : "Not press-ganged: " + r.failure().name().toLowerCase(Locale.ROOT)), false);
        return r.success() ? 1 : 0;
    }

    private static int release(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        PrisonerOutcomes.ReleaseResult r = PrisonerOutcomes.release(living(c, "prisoner"));
        c.getSource().sendSuccess(() -> Component.literal(r.success() ? "Released" : "Not released: " + r.failure().name().toLowerCase(Locale.ROOT)), false);
        return r.success() ? 1 : 0;
    }
}
