package com.richardsenger.piratesnships.core.advancement;

import com.richardsenger.piratesnships.platform.Services;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pays the experience of the mod's advancements (ADV1). The generated advancements carry no reward of their own, so
 * {@code advancements.experience_scale} can scale it: once an advancement of {@link AdvancementTree} is done, the
 * player gets {@link AdvancementTree#experience} once (remembered in {@link AdvancementRecord#rewarded}). Settled right
 * after every mod trigger and once a second for the advancements on vanilla criteria ({@link AdvancementWatch}).
 */
public final class AdvancementXp {

    private AdvancementXp() {
    }

    public static void settle(ServerPlayer player) {
        if (!AdvancementConfig.ENABLED.get() || player.getServer() == null) return;
        ServerAdvancementManager manager = player.getServer().getAdvancements();
        AdvancementRecord before = Services.ATTACHMENTS.get(player, AdvancementAttachments.RECORD);
        AdvancementRecord rec = before;
        double scale = AdvancementConfig.EXPERIENCE_SCALE.get();
        for (AdvancementTree.Node node : AdvancementTree.nodes()) {
            if (rec.rewarded().contains(node.id())) continue;
            AdvancementHolder holder = manager.get(com.richardsenger.piratesnships.Constants.id(node.path()));
            if (holder == null || !player.getAdvancements().getOrStartProgress(holder).isDone()) continue;
            int xp = AdvancementTree.experience(node, scale);
            if (xp > 0) player.giveExperiencePoints(xp);
            rec = rec.withRewarded(node.id());
        }
        if (rec != before) Services.ATTACHMENTS.set(player, AdvancementAttachments.RECORD, rec);
    }
}
