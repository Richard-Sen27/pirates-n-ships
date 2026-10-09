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
            new com.richardsenger.piratesnships.station.StationModule(),
            new com.richardsenger.piratesnships.crew.provisions.ProvisionsModule(),
            new com.richardsenger.piratesnships.ship.hull.HullModule(),
            new com.richardsenger.piratesnships.ship.assembly.AssemblyModule(),
            new com.richardsenger.piratesnships.ship.screen.ShipScreenModule(),
            new com.richardsenger.piratesnships.core.settings.SettingsModule(),
            new com.richardsenger.piratesnships.combat.content.CombatContentModule(),
            new com.richardsenger.piratesnships.trade.content.TradeContentModule(),
            new com.richardsenger.piratesnships.crew.content.CrewContentModule(),
            new com.richardsenger.piratesnships.law.content.LawContentModule(),
            new com.richardsenger.piratesnships.law.brig.BrigModule(),
            new com.richardsenger.piratesnships.ship.decor.ShipDecorModule(),
            new com.richardsenger.piratesnships.ship.rigging.RiggingModule(),
            new com.richardsenger.piratesnships.trade.TradeModule(),
            new com.richardsenger.piratesnships.trade.fees.FeesModule(),
            new com.richardsenger.piratesnships.combat.melee.MeleeModule(),
            new com.richardsenger.piratesnships.combat.firearms.FirearmsModule(),
            new com.richardsenger.piratesnships.combat.cannon.CannonModule(),
            new com.richardsenger.piratesnships.combat.grapple.GrappleModule(),
            new com.richardsenger.piratesnships.combat.boarding.BoardingModule(),
            new com.richardsenger.piratesnships.audio.AudioModule(),
            new com.richardsenger.piratesnships.mob.MobModule(),
            new com.richardsenger.piratesnships.mob.captain.CaptainModule(),
            new com.richardsenger.piratesnships.mob.harbor.HarborMasterModule(),
            new com.richardsenger.piratesnships.mob.squad.SquadModule(),
            new com.richardsenger.piratesnships.survival.SurvivalModule(),
            new com.richardsenger.piratesnships.seachest.SeaChestModule(),
            new com.richardsenger.piratesnships.hazards.HazardsModule(),
            new com.richardsenger.piratesnships.guide.GuideModule(),
            new com.richardsenger.piratesnships.apparel.ApparelModule(),
            new com.richardsenger.piratesnships.chart.ChartModule(),
            new com.richardsenger.piratesnships.world.WorldModule(),
            new com.richardsenger.piratesnships.worldsim.faction.FactionModule(),
            new com.richardsenger.piratesnships.rpg.RpgModule(),
            new com.richardsenger.piratesnships.rpg.career.CareerModule(),
            new com.richardsenger.piratesnships.rpg.quest.QuestModule(),
            new com.richardsenger.piratesnships.crew.hiring.HiringModule(),
            new com.richardsenger.piratesnships.worldsim.voyage.VoyageModule(),
            new com.richardsenger.piratesnships.worldsim.materialize.MaterializeModule(),
            new com.richardsenger.piratesnships.worldsim.navy.NavyModule(),
            new com.richardsenger.piratesnships.worldsim.raid.RaidModule(),
            new com.richardsenger.piratesnships.worldsim.captain.CaptainVoyageModule()
    );
}
