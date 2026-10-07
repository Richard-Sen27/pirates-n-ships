package com.richardsenger.piratesnships.law.brig;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.Services;
import com.richardsenger.piratesnships.platform.event.CommonEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.List;

/** The {@code law.brig} module: prisoners, shackles behavior, brig cells, escapes and outcomes (design.md §13.3). */
public final class BrigModule implements ModModule {

    @Override
    public String id() {
        return "law.brig";
    }

    @Override
    public void registerConfig() {
        BrigConfig.init();
    }

    @Override
    public void registerContent() {
        Services.ATTACHMENTS.register(BrigService.PRISONER);
    }

    @Override
    public void registerEvents() {
        CommonEvents.SERVER_TICK_END.register(BrigService::tick);
        CommonEvents.SERVER_STOPPED.register(BrigService::onServerStopped);
        CommonEvents.ENTITY_JOIN_LEVEL.register(BrigService::onJoin);
        CommonEvents.LIVING_INCOMING_DAMAGE.register(BrigService::onDamage);
        CommonEvents.BLOCK_BREAK.register((level, pos, state, player) -> BrigService.onBlockBreak(player));
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> BrigCommands.register(dispatcher));
        CommonEvents.ENTITY_INTERACT.register(PrisonerInteractions::onEntityInteract);
    }

    @Override
    public void gatherData(DataContributions data) {
        data.entityTypeTags(tags -> tags.tag(BrigService.NOT_CAPTURABLE)
                .add(EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN)
                .addOptionalTag(ResourceLocation.fromNamespaceAndPath("c", "bosses")));
        String m = "message.pirates_n_ships.brig.";
        data.lang(lang -> lang
                .add(m + "capture.ok", "%s is in shackles")
                .add(m + "capture.self", "You can't shackle yourself")
                .add(m + "capture.dead", "%s is beyond capture")
                .add(m + "capture.already_prisoner", "%s is already a prisoner")
                .add(m + "capture.not_capturable", "%s can't be shackled")
                .add(m + "capture.player_capture_disabled", "Capturing players is disabled on this server")
                .add(m + "capture.no_bounty", "Only players with a bounty can be captured")
                .add(m + "capture.too_healthy", "%s is still too strong. Weaken them first")
                .add(m + "captured_player", "You were captured by %s")
                .add(m + "player_freed", "You slipped out of your shackles")
                .add(m + "lead.start", "You lead %s")
                .add(m + "lead.stop", "You let go of %s's chain")
                .add(m + "lead.take_over", "You take %s's chain")
                .add(m + "lead.someone_else", "This prisoner is led by %s")
                .add(m + "lead.lost", "You lost hold of %s")
                .add(m + "in_cell", "%s is locked in the brig")
                .add(m + "escaped", "%s escaped!")
                .add(m + "door.locked", "Brig door locked")
                .add(m + "door.unlocked", "Brig door unlocked")
                .add(m + "door.is_locked", "The brig door is locked")
                .add(m + "door.not_owner", "Only the door's owner can lock or unlock it")
                .add(m + "pressgang.done", "%s, grumbling: \"Aye... Captain.\" Pressed into your crew")
                .add(m + "pressgang.not_on_ship", "Bring them aboard your ship first.")
                .add(m + "pressgang.not_your_ship", "This isn't your ship to crew.")
                .add(m + "pressgang.not_a_sailor", "Only sailors can be pressed into a crew")
                .add(m + "pressgang.not_a_prisoner", "Only a shackled sailor can be pressed into a crew")
                .add(m + "pressgang.disabled", "Press-ganging is disabled on this server")
                .add(m + "release.done", "\"Go, before I change my mind.\" %s is free"));
        String o = "message.pirates_n_ships.officer.";
        data.lang(lang -> lang
                .add(o + "fine.paid", "\"That settles %s points. Mind yourself.\" (%s doubloons)")
                .add(o + "fine.cleared", "\"That settles %s points. Your slate is clean. Mind yourself.\" (%s doubloons)")
                .add(o + "fine.nothing_owed", "\"You owe the Crown nothing.\"")
                .add(o + "fine.too_poor", "\"That won't settle a single point. %s doubloons a point.\"")
                .add(o + "fine.notorious", "\"No coin buys a pardon for the likes of you.\"")
                .add(o + "prisoner.ransomed", "\"The Crown thanks you.\" %s is ransomed for %s doubloons")
                .add(o + "prisoner.no_port", "Ransoms are only paid at a navy outpost"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(BrigGameTests.class, PrisonerInteractionGameTests.class);
    }
}
