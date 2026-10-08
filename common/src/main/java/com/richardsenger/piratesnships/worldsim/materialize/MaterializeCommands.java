package com.richardsenger.piratesnships.worldsim.materialize;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.trade.TradeService;
import com.richardsenger.piratesnships.worldsim.lane.Lane;
import com.richardsenger.piratesnships.worldsim.voyage.Voyage;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageConfig;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageData;
import com.richardsenger.piratesnships.worldsim.voyage.VoyageKind;
import com.richardsenger.piratesnships.worldsim.voyage.Voyages;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /pirates world voyages materialize <id>} and {@code dematerialize <id>}; {@code spawn near <convoy|patrol|raid>}
 * sends a voyage across the sea 48 blocks in front of the player (overworld) and materialises it at once, for
 * playtests (operators only, permission 2).
 */
public final class MaterializeCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".world.voyages.";
    /** Blocks ahead of the player the test route passes. */
    static final int AHEAD = 48;
    /** Half the test route's length. */
    static final int HALF = 300;

    private MaterializeCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var ids = (com.mojang.brigadier.suggestion.SuggestionProvider<CommandSourceStack>) (c, b) -> SharedSuggestionProvider.suggest(
                Voyages.active(c.getSource().getServer()).stream().map(Voyage::shortId), b);
        dispatcher.register(Commands.literal("pirates").then(Commands.literal("world").requires(s -> s.hasPermission(2))
                .then(Commands.literal("voyages").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("materialize").then(Commands.argument("id", StringArgumentType.word()).suggests(ids)
                                .executes(MaterializeCommands::materialize)))
                        .then(Commands.literal("dematerialize").then(Commands.argument("id", StringArgumentType.word()).suggests(ids)
                                .executes(MaterializeCommands::dematerialize)))
                        .then(Commands.literal("spawn").then(Commands.literal("near")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("convoy", "patrol", "raid"), b))
                                        .executes(MaterializeCommands::spawnNear)))))));
    }

    private static Optional<Voyage> find(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "id");
        Optional<Voyage> v = Voyages.find(c.getSource().getServer(), id);
        if (v.isEmpty()) c.getSource().sendFailure(Component.translatable(KEY + "unknown", id));
        return v;
    }

    private static int materialize(CommandContext<CommandSourceStack> c) {
        Optional<Voyage> v = find(c);
        if (v.isEmpty()) return 0;
        Materializer.Outcome o = Materializer.materialize(c.getSource().getServer(), v.get().id());
        return report(c, v.get(), o);
    }

    private static int report(CommandContext<CommandSourceStack> c, Voyage v, Materializer.Outcome o) {
        Component msg = Component.translatable(KEY + "materialize." + o.name().toLowerCase(Locale.ROOT), v.shortId());
        if (o.spawned()) {
            c.getSource().sendSuccess(() -> msg, true);
            return 1;
        }
        c.getSource().sendFailure(msg);
        return 0;
    }

    private static int dematerialize(CommandContext<CommandSourceStack> c) {
        Optional<Voyage> v = find(c);
        if (v.isEmpty()) return 0;
        if (!Materializer.dematerialize(c.getSource().getServer(), v.get().id())) {
            c.getSource().sendFailure(Component.translatable(KEY + "not_materialised", v.get().shortId()));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "dematerialised", v.get().shortId()), true);
        return 1;
    }

    private static int spawnNear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ServerLevel level = c.getSource().getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            c.getSource().sendFailure(Component.translatable(KEY + "overworld_only"));
            return 0;
        }
        VoyageKind kind;
        try {
            kind = VoyageKind.valueOf(StringArgumentType.getString(c, "kind").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.translatable(KEY + "unknown_kind"));
            return 0;
        }
        RandomSource rng = level.getRandom();
        double yaw = Math.toRadians(player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double sx = -fz, sz = fx;
        double cx = player.getX() + fx * AHEAD, cz = player.getZ() + fz * AHEAD;
        List<Lane.Point> route = List.of(
                new Lane.Point((int) Math.floor(cx - sx * HALF), (int) Math.floor(cz - sz * HALF)),
                new Lane.Point((int) Math.floor(cx + sx * HALF), (int) Math.floor(cz + sz * HALF)));
        Map<ResourceLocation, Integer> cargo = new LinkedHashMap<>();
        if (kind == VoyageKind.CONVOY) {
            List<ResourceLocation> goods = new ArrayList<>(TradeService.goods(level).tradeableIds());
            for (int i = 0; i < 2 && !goods.isEmpty(); i++) {
                cargo.put(goods.remove(rng.nextInt(goods.size())), VoyageConfig.CONVOY_CARGO_UNITS.get());
            }
        }
        List<String> templates = switch (kind) {
            case CONVOY -> VoyageConfig.MERCHANT_TEMPLATES.get();
            case PATROL -> VoyageConfig.NAVY_TEMPLATES.get();
            case RAID -> VoyageConfig.PIRATE_TEMPLATES.get();
        };
        ResourceLocation here = Constants.id("command/near");
        Voyage v = Voyage.depart(UUID.randomUUID(), kind, kind.defaultFaction(), Voyages.pickTemplate(templates, rng), here, here,
                route, cargo, level.getGameTime()).withProgress(HALF - 30);
        VoyageData.get(c.getSource().getServer()).put(v);
        Materializer.Outcome o = Materializer.materialize(c.getSource().getServer(), v.id());
        c.getSource().sendSuccess(() -> Component.translatable(KEY + "spawned_near", kind.getSerializedName(), v.shortId()), true);
        return report(c, v, o);
    }

    public static void lang(LangBuilder lang) {
        lang.add(KEY + "materialize.spawned", "Voyage %s is a real ship now")
                .add(KEY + "materialize.waiting_for_chunks", "Voyage %s waits for its chunks to load; try again in a moment")
                .add(KEY + "materialize.no_water", "Voyage %s is not over open water")
                .add(KEY + "materialize.obstructed", "Voyage %s has no room for its ship there")
                .add(KEY + "materialize.failed", "Voyage %s could not be placed (see the log)")
                .add(KEY + "materialize.cap", "Too many NPC ships are real already (max_materialized); voyage %s stays a record")
                .add(KEY + "materialize.not_sailing", "Voyage %s is not sailing as a record")
                .add(KEY + "materialize.unknown", "Voyage %s is gone")
                .add(KEY + "not_materialised", "Voyage %s is not a real ship")
                .add(KEY + "dematerialised", "Voyage %s is a record again")
                .add(KEY + "spawned_near", "A %s voyage (%s) crosses the sea in front of you")
                .add(KEY + "overworld_only", "Test voyages sail in the overworld only")
                .add(KEY + "unknown_kind", "Kind must be convoy, patrol or raid");
    }
}
