package com.richardsenger.piratesnships.mob.squad;

import com.richardsenger.piratesnships.core.ModModule;
import com.richardsenger.piratesnships.core.datagen.DataContributions;
import com.richardsenger.piratesnships.platform.event.CommonEvents;

import java.util.List;

/**
 * Officers leading squads (work package MOB2, docs/design.md §9): a navy outpost's officer leads
 * {@code mobs.squad.size} soldiers of the garrison on a patrol route around the fort's court, walls and quay
 * ({@link SquadRoutes}), in file ({@link FileFollowing}), pausing at each waypoint, back to the garrison posts at night
 * and after the round; the squad fights as one ({@link SquadCombat}) and refills from the garrison. Config
 * {@code mobs.squad} ({@link SquadConfig}), operators' {@code /pirates mob squad} ({@link SquadCommands}).
 */
public final class SquadModule implements ModModule {

    @Override
    public String id() {
        return "mob.squad";
    }

    @Override
    public void registerConfig() {
        SquadConfig.init();
    }

    @Override
    public void registerContent() {
        SquadAttachments.init();
    }

    @Override
    public void registerEvents() {
        CommonEvents.REGISTER_COMMANDS.register((dispatcher, context, selection) -> SquadCommands.register(dispatcher));
        CommonEvents.SERVER_STOPPED.register(server -> SquadService.clear());
    }

    @Override
    public void gatherData(DataContributions data) {
        data.lang(lang -> lang
                .add(SquadCommands.KEY_NONE, "No navy officer leading a squad within %s blocks")
                .add(SquadCommands.KEY_INFO, "Squad of %s: %s, waypoint %s of %s, %s soldiers, next patrol in %s s")
                .add(SquadCommands.KEY_PATROL, "%s sets off on patrol with %s soldiers (%s waypoints)")
                .add(SquadCommands.KEY_PATROL_REFUSED, "%s can't patrol: squads are disabled (mobs.squad.enabled) or he has no route")
                .add(SquadCommands.KEY_RETURN, "%s leads his squad back to the posts")
                .add(SquadCommands.KEY_RETURN_REFUSED, "%s and his squad are already at their posts"));
    }

    @Override
    public List<Class<?>> gameTestClasses() {
        return List.of(SquadGameTests.class);
    }
}
