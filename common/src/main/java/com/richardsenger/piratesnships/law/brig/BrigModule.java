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
                .add(m + "door.not_owner", "Only the door's owner can lock or unlock it"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(BrigGameTests.class);
    }
}
