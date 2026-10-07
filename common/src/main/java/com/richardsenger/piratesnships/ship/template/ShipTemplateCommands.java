package com.richardsenger.piratesnships.ship.template;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.richardsenger.piratesnships.Constants;
import com.richardsenger.piratesnships.core.datagen.LangBuilder;
import com.richardsenger.piratesnships.ship.assembly.AssemblyResult;
import com.richardsenger.piratesnships.ship.template.ShipTemplatePlacer.Result;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Operator commands (permission 2) for ship templates:
 * <pre>
 * /pirates ship templates                              every loaded template with its size, helm and waterline
 * /pirates ship place &lt;template&gt; [force] [assemble]   places the template in front of the caller, bow pointing
 *                                                      where the caller looks; force ignores obstructions, assemble
 *                                                      turns it into a ship at its helm
 * </pre>
 * A template id without a namespace means ours ({@code starter_sloop} = {@code pirates_n_ships:starter_sloop}).
 */
public final class ShipTemplateCommands {

    static final String KEY = "commands." + Constants.MOD_ID + ".ship_template.";
    public static final String KEY_PLACED = KEY + "placed";
    public static final String KEY_SEA_LEVEL = KEY + "sea_level";
    public static final String KEY_UNKNOWN = KEY + "unknown";
    public static final String KEY_MISSING_STRUCTURE = KEY + "missing_structure";
    public static final String KEY_OBSTRUCTED = KEY + "obstructed";
    public static final String KEY_OUT_OF_WORLD = KEY + "out_of_world";
    public static final String KEY_NO_HELM = KEY + "no_helm";
    public static final String KEY_LIST_HEADER = KEY + "list";
    public static final String KEY_LIST_ENTRY = KEY + "list_entry";
    public static final String KEY_LIST_EMPTY = KEY + "list_empty";
    public static final String KEY_HULL_ONLY = KEY + "hull_only";

    private ShipTemplateCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> template = Commands.argument("template", ResourceLocationArgument.id())
                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(ShipTemplates.TYPE.server().ids(), b))
                .executes(c -> place(c, false, false))
                .then(Commands.literal("assemble").executes(c -> place(c, false, true)))
                .then(Commands.literal("force").executes(c -> place(c, true, false))
                        .then(Commands.literal("assemble").executes(c -> place(c, true, true))));
        dispatcher.register(Commands.literal("pirates")
                .then(Commands.literal("ship").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("templates").executes(ShipTemplateCommands::list))
                        .then(Commands.literal("place").then(template))));
    }

    /** English strings of the commands and our template names (datagen). */
    public static void lang(LangBuilder lang) {
        lang.add(KEY_PLACED, "Placed %s (%s blocks) at %s %s %s, bow to the %s, waterline at y %s")
                .add(KEY_SEA_LEVEL, "No water under the ship: the waterline was put at sea level")
                .add(KEY_UNKNOWN, "Unknown ship template %s (see /pirates ship templates)")
                .add(KEY_MISSING_STRUCTURE, "Ship template %s: its structure %s is missing")
                .add(KEY_OBSTRUCTED, "Something is in the way at %s %s %s (add force to place anyway)")
                .add(KEY_OUT_OF_WORLD, "The ship would stick out of the world at %s %s %s")
                .add(KEY_NO_HELM, "Ship template %s has no helm, so it can't be assembled")
                .add(KEY_LIST_HEADER, "%s ship templates:")
                .add(KEY_LIST_ENTRY, "%s (%s): %s×%s×%s, helm %s, waterline row %s, price %s")
                .add(KEY_LIST_EMPTY, "No ship templates are loaded")
                .add(KEY_HULL_ONLY, "none (hull only)")
                .add(ShipTemplates.STARTER_SLOOP.name(), "Starter Sloop")
                .add(ShipTemplates.STARTER_SLOOP_BASIC.name(), "Starter Sloop (basic)");
    }

    /** The template id as typed: a bare path that is not a known {@code minecraft:} id means ours. */
    public static ResourceLocation resolve(ResourceLocation typed) {
        if (typed.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE) && !ShipTemplates.TYPE.server().contains(typed)) {
            return ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, typed.getPath());
        }
        return typed;
    }

    private static int place(CommandContext<CommandSourceStack> c, boolean force, boolean assemble) {
        return place(c.getSource(), resolve(ResourceLocationArgument.getId(c, "template")), force, assemble).outcome().success ? 1 : 0;
    }

    /** The command body: places at the source's position and yaw (snapped to 90°) and reports the result. */
    public static Result place(CommandSourceStack source, ResourceLocation id, boolean force, boolean assemble) {
        Direction facing = TemplatePlacement.snapFacing(source.getRotation().y);
        BlockPos feet = BlockPos.containing(source.getPosition());
        @Nullable Player player = source.getEntity() instanceof Player p ? p : null;
        Result r = ShipTemplatePlacer.place(source.getLevel(), id, feet, facing, force, assemble, player);
        report(source, r);
        return r;
    }

    private static void report(CommandSourceStack source, Result r) {
        String idText = r.id().toString();
        switch (r.outcome()) {
            case PLACED -> {
                BlockPos o = r.origin();
                Component name = Component.translatable(r.template().name());
                source.sendSuccess(() -> Component.translatable(KEY_PLACED, name, r.blocks(), o.getX(), o.getY(), o.getZ(),
                        facingName(r), r.surfaceY()), true);
                if (r.seaLevel()) {
                    source.sendSuccess(() -> Component.translatable(KEY_SEA_LEVEL).withStyle(ChatFormatting.YELLOW), false);
                }
                AssemblyResult a = r.assembly();
                if (a != null) {
                    if (a.success()) {
                        source.sendSuccess(a::message, true);
                    } else {
                        source.sendFailure(a.message());
                    }
                }
            }
            case UNKNOWN_TEMPLATE -> source.sendFailure(Component.translatable(KEY_UNKNOWN, idText));
            case MISSING_STRUCTURE -> source.sendFailure(Component.translatable(KEY_MISSING_STRUCTURE, idText,
                    r.template().structure().toString()));
            case OBSTRUCTED -> source.sendFailure(Component.translatable(KEY_OBSTRUCTED, r.where().getX(), r.where().getY(), r.where().getZ()));
            case OUT_OF_WORLD -> source.sendFailure(Component.translatable(KEY_OUT_OF_WORLD, r.where().getX(), r.where().getY(), r.where().getZ()));
            case NO_HELM -> source.sendFailure(Component.translatable(KEY_NO_HELM, idText));
        }
    }

    private static String facingName(Result r) {
        Direction bow = r.template().bow();
        Direction facing = switch (r.rotation()) {
            case CLOCKWISE_90 -> bow.getClockWise();
            case CLOCKWISE_180 -> bow.getOpposite();
            case COUNTERCLOCKWISE_90 -> bow.getCounterClockWise();
            case NONE -> bow;
        };
        return facing.getName();
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        Map<ResourceLocation, ShipTemplate> all = ShipTemplates.TYPE.server().all();
        if (all.isEmpty()) {
            s.sendFailure(Component.translatable(KEY_LIST_EMPTY));
            return 0;
        }
        s.sendSuccess(() -> Component.translatable(KEY_LIST_HEADER, all.size()), false);
        all.forEach((id, t) -> {
            var structure = ShipTemplatePlacer.structure(s.getLevel(), t).orElse(null);
            if (structure == null) {
                s.sendFailure(Component.translatable(KEY_MISSING_STRUCTURE, id.toString(), t.structure().toString()));
                return;
            }
            Vec3i size = structure.getSize();
            BlockPos helm = ShipTemplatePlacer.helm(t, ShipTemplatePlacer.blocks(structure, s.getLevel().holderLookup(
                    net.minecraft.core.registries.Registries.BLOCK)));
            Component helmText = helm == null ? Component.translatable(KEY_HULL_ONLY)
                    : Component.literal(String.format(Locale.ROOT, "%d %d %d", helm.getX(), helm.getY(), helm.getZ()));
            s.sendSuccess(() -> Component.translatable(KEY_LIST_ENTRY, id.toString(), Component.translatable(t.name()),
                    size.getX(), size.getY(), size.getZ(), helmText, t.waterlineFor(helm), t.price()), false);
        });
        return all.size();
    }
}
