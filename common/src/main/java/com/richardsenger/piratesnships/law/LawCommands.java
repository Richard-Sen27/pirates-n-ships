package com.richardsenger.piratesnships.law;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.law.bounty.BountyBoard;
import com.richardsenger.piratesnships.law.crime.CrimeType;
import com.richardsenger.piratesnships.law.crime.CriminalRecord;
import com.richardsenger.piratesnships.law.crime.WantedLevel;
import com.richardsenger.piratesnships.law.world.CrimeLog;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Operator debug commands (permission 2) to exercise the law system before navy mobs and notice boards exist:
 * <pre>
 * /pirates law score get|set|add &lt;target&gt; [value]
 * /pirates law crime &lt;target&gt; &lt;crime&gt;
 * /pirates law fine &lt;target&gt; &lt;doubloons&gt;
 * /pirates law last &lt;target&gt;          (last crime reported for the target, any outcome)
 * /pirates law hostile &lt;target&gt;       (would the navy attack the target on sight?)
 * /pirates law bounty list | place &lt;target&gt; &lt;amount&gt; | claim &lt;target&gt; dead|alive | claim proof | clear &lt;target&gt;
 * </pre>
 */
public final class LawCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".law.";

    private static final SimpleCommandExceptionType NOT_LIVING =
            new SimpleCommandExceptionType(Component.translatable(KEY + "not_living"));
    private static final SimpleCommandExceptionType UNKNOWN_CRIME =
            new SimpleCommandExceptionType(Component.translatable(KEY + "unknown_crime"));

    private LawCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("law")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("score")
                        .then(Commands.literal("get").then(Commands.argument("target", EntityArgument.entity())
                                .executes(LawCommands::scoreGet)))
                        .then(Commands.literal("set").then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(c -> scoreChange(c, false)))))
                        .then(Commands.literal("add").then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(c -> scoreChange(c, true))))))
                .then(Commands.literal("crime").then(Commands.argument("target", EntityArgument.entity())
                        .then(Commands.argument("crime", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(CrimeType.values()).map(CrimeType::id), b))
                                .executes(LawCommands::crime))))
                .then(Commands.literal("last").then(Commands.argument("target", EntityArgument.entity())
                        .executes(LawCommands::lastCrime)))
                .then(Commands.literal("hostile").then(Commands.argument("target", EntityArgument.entity())
                        .executes(LawCommands::hostile)))
                .then(Commands.literal("fine").then(Commands.argument("target", EntityArgument.entity())
                        .then(Commands.argument("doubloons", IntegerArgumentType.integer(0))
                                .executes(LawCommands::fine))))
                .then(Commands.literal("bounty")
                        .then(Commands.literal("list").executes(LawCommands::bountyList))
                        .then(Commands.literal("place").then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(LawCommands::bountyPlace))))
                        .then(Commands.literal("claim")
                                .then(Commands.literal("proof").executes(LawCommands::bountyClaimProof))
                                .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.literal("dead").executes(c -> bountyClaim(c, BountyBoard.ClaimMethod.DEAD_WITH_PROOF)))
                                .then(Commands.literal("alive").executes(c -> bountyClaim(c, BountyBoard.ClaimMethod.ALIVE)))))
                        .then(Commands.literal("clear").then(Commands.argument("target", EntityArgument.entity())
                                .executes(LawCommands::bountyClear))))));
    }

    /** Translation key of a wanted level name. */
    public static String wantedKey(WantedLevel level) {
        return Constants.MOD_ID + ".law.wanted." + level.name().toLowerCase(Locale.ROOT);
    }

    private static LivingEntity living(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "target");
        if (e instanceof LivingEntity l) return l;
        throw NOT_LIVING.create();
    }

    private static int scoreGet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        LivingEntity target = living(c);
        CriminalRecord r = LawService.record(target);
        WantedLevel level = LawService.wantedLevel(target);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "score.get", target.getDisplayName(),
                r.displayScore(), Component.translatable(wantedKey(level)), r.totalCrimes()), false);
        return r.displayScore();
    }

    private static int scoreChange(CommandContext<CommandSourceStack> c, boolean add) throws CommandSyntaxException {
        LivingEntity target = living(c);
        double value = DoubleArgumentType.getDouble(c, "value");
        double newScore = add ? LawService.record(target).score() + value : value;
        LawService.setScore(target, newScore);
        CriminalRecord r = LawService.record(target);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "score.set", target.getDisplayName(),
                r.displayScore()), true);
        return r.displayScore();
    }

    private static int crime(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        LivingEntity target = living(c);
        String id = StringArgumentType.getString(c, "crime");
        CrimeType type = Arrays.stream(CrimeType.values()).filter(t -> t.id().equals(id)).findFirst()
                .orElseThrow(UNKNOWN_CRIME::create);
        // The command source acts as the victim, so repeating the command shows the repeat window
        Entity victim = c.getSource().getEntity();
        CriminalRecord.CrimeResult result = LawService.reportCrime(target, type, victim);
        CriminalRecord r = LawService.record(target);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "crime", target.getDisplayName(), Component.translatable(type.nameKey()),
                outcome(result.outcome()), String.format(Locale.ROOT, "%.1f", result.pointsAdded()), r.displayScore()), true);
        return result.counted() ? 1 : 0;
    }

    private static int fine(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        LivingEntity target = living(c);
        int doubloons = IntegerArgumentType.getInteger(c, "doubloons");
        CriminalRecord.FineResult result = LawService.payFine(target, doubloons);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "fine", target.getDisplayName(),
                result.doubloonsSpent(), String.format(Locale.ROOT, "%.1f", result.pointsRemoved()),
                outcome(result.outcome()), result.record().displayScore()), true);
        return result.doubloonsSpent();
    }

    private static int bountyList(CommandContext<CommandSourceStack> c) {
        List<BountyBoard.Notice> notices = LawService.notices(c.getSource().getServer());
        if (notices.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "bounty.list.empty"), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "bounty.list.header", notices.size()), false);
        for (BountyBoard.Notice n : notices) {
            Component line = Component.translatable(n.navy() ? KEY + "bounty.list.entry_navy" : KEY + "bounty.list.entry",
                    n.target().name(), n.total(), n.count());
            c.getSource().sendSuccess(() -> line, false);
        }
        return notices.size();
    }

    private static int bountyPlace(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer payer = c.getSource().getPlayerOrException();
        Entity target = EntityArgument.getEntity(c, "target");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        BountyBoard.PlaceResult result = LawService.placeBounty(payer, target, amount);
        long total = LawService.bountyTotal(c.getSource().getServer(), target.getUUID());
        if (result.placed()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "bounty.place.success", amount,
                    target.getDisplayName(), total), true);
        } else {
            c.getSource().sendFailure(Component.translatable(KEY + "bounty.place.failed", outcome(result.outcome())));
        }
        return result.placed() ? 1 : 0;
    }

    private static int bountyClaim(CommandContext<CommandSourceStack> c, BountyBoard.ClaimMethod method) throws CommandSyntaxException {
        ServerPlayer claimant = c.getSource().getPlayerOrException();
        LivingEntity target = living(c);
        BountyBoard.ClaimResult result = LawService.claimBounty(target, claimant, method);
        if (result.success()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "bounty.claim.success",
                    target.getDisplayName(), result.payout(), result.claimed().size()), true);
        } else {
            c.getSource().sendFailure(Component.translatable(KEY + "bounty.claim.failed", outcome(result.outcome())));
        }
        return result.payout();
    }

    private static int bountyClaimProof(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer claimant = c.getSource().getPlayerOrException();
        ItemStack stack = claimant.getMainHandItem();
        LawService.ProofClaim result = LawService.claimWithProof(claimant, stack);
        if (result.success()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "bounty.claim.success",
                    result.proof().targetName(), result.payout(), result.bounties()), true);
        } else {
            c.getSource().sendFailure(Component.translatable(KEY + "bounty.claim.failed", outcome(result.outcome())));
        }
        return result.payout();
    }

    private static int lastCrime(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(c, "target");
        var entry = CrimeLog.last(target.getUUID());
        if (entry.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable(KEY + "last.none", target.getDisplayName()), false);
            return 0;
        }
        var e = entry.get();
        long ago = (LawService.now(c.getSource().getServer()) - e.gameTime()) / 20;
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "last", target.getDisplayName(), Component.translatable(e.type().nameKey()),
                e.victim().isEmpty() ? "-" : e.victim(), outcome(e.outcome()),
                String.format(Locale.ROOT, "%.1f", e.points()), ago), false);
        return 1;
    }

    private static int hostile(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        LivingEntity target = living(c);
        // No navy mobs yet: ask for an unspecified navy observer
        boolean answer = LawService.navyShouldAttack(null, target);
        c.getSource().sendSuccess(() -> Component.translatable(KEY + (answer ? "hostile.yes" : "hostile.no"),
                target.getDisplayName(), Component.translatable(wantedKey(LawService.wantedLevel(target)))), false);
        return answer ? 1 : 0;
    }

    private static int bountyClear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity target = EntityArgument.getEntity(c, "target");
        BountyBoardData data = BountyBoardData.get(c.getSource().getServer());
        data.setBoard(data.board().clearTarget(target.getUUID()));
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "bounty.clear", target.getDisplayName()), true);
        return 1;
    }

    private static String outcome(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT);
    }
}
