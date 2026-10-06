package com.richardsenger.piratesnships.core;

import java.util.List;

/**
 * The list of feature modules. <b>Only the orchestrator edits this file.</b> A feature work package adds its module
 * by asking for exactly one line here; everything else (content, config, payloads, events, datagen, GameTests)
 * lives in the module's own package and is reached through its {@link ModModule} implementation.
 */
public final class ModModules {

    private ModModules() {
    }

    /** All modules, in initialization order. {@code core} stays first. */
    public static final List<ModModule> ALL = List.of(
            new CoreModule(),
            new com.richardsenger.piratesnships.law.LawModule(),
            new com.richardsenger.piratesnships.sailing.SailingModule(),
            new com.richardsenger.piratesnships.crew.provisions.ProvisionsModule(),
            new com.richardsenger.piratesnships.ship.hull.HullModule(),
            new com.richardsenger.piratesnships.ship.assembly.AssemblyModule(),
            new com.richardsenger.piratesnships.core.settings.SettingsModule(),
            new com.richardsenger.piratesnships.combat.content.CombatContentModule(),
            new com.richardsenger.piratesnships.trade.content.TradeContentModule(),
            new com.richardsenger.piratesnships.crew.content.CrewContentModule(),
            new com.richardsenger.piratesnships.law.content.LawContentModule(),
            new com.richardsenger.piratesnships.ship.decor.ShipDecorModule(),
            new com.richardsenger.piratesnships.trade.TradeModule(),
            new com.richardsenger.piratesnships.combat.melee.MeleeModule()
    );
}
