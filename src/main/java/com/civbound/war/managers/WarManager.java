package com.civbound.war;

import me.angeschossen.lands.api.land.Land;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class WarManager {

    private final CivboundWar plugin;
    private final WarConfig config;
    private final LandUtil landUtil;
    private final CapStore capStore;

    private FeedbackManager feedback;
    private ReplayManager replay;
    private BeaconManager beacons;
    private PreBattleManager preBattle;

    private final Map<String, Campaign> campaigns = new LinkedHashMap<>();
    
    private final Map<String, Campaign> byName = new LinkedHashMap<>();

    public WarManager(CivboundWar plugin, WarConfig config, LandUtil landUtil, CapStore capStore) {
        this.plugin = plugin;
        this.config = config;
        this.landUtil = landUtil;
        this.capStore = capStore;
    }

    public void setFeedback(FeedbackManager feedback) {
        this.feedback = feedback;
    }

    public void setReplay(ReplayManager replay) {
        this.replay = replay;
    }

    public void setBeacons(BeaconManager beacons) {
        this.beacons = beacons;
    }

    public void setPreBattle(PreBattleManager preBattle) {
        this.preBattle = preBattle;
    }

    public WarConfig getConfig() {
        return config;
    }

    public LandUtil getLandUtil() {
        return landUtil;
    }

    public CapStore getCapStore() {
        return capStore;
    }

    public Collection<Campaign> getCampaigns() {
        return campaigns.values();
    }

    private static String idOf(String attacker, String defender) {
        return attacker.toLowerCase(Locale.ROOT) + "_vs_" + defender.toLowerCase(Locale.ROOT);
    }

    public Campaign getCampaign(String attacker, String defender) {
        return campaigns.get(idOf(attacker, defender));
    }

    
    public Campaign getByName(String name) {
        if (name == null) {
            return null;
        }
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    

    public Campaign resolve(String token) {
        Campaign c = getByName(token);
        return c != null ? c : findCampaignInvolving(token);
    }

    
    public Campaign findCampaignInvolving(String landName) {
        for (Campaign c : campaigns.values()) {
            if (c.getAttackerLand().equalsIgnoreCase(landName) || c.getDefenderLand().equalsIgnoreCase(landName)) {
                return c;
            }
        }
        return null;
    }

    public List<Battle> activeBattles() {
        List<Battle> out = new ArrayList<>();
        for (Campaign c : campaigns.values()) {
            if (c.isBattleActive()) {
                out.add(c.getCurrentBattle());
            }
        }
        return out;
    }

    public boolean anyBattleActive() {
        for (Campaign c : campaigns.values()) {
            if (c.isBattleActive()) {
                return true;
            }
        }
        return false;
    }

    
    public enum StartResult {
        CAMPAIGN_CREATED,
        BATTLE_STARTED,
        BATTLE_ALREADY_ACTIVE,
        SERIES_FINISHED,
        SAME_LAND,
        NO_ATTACKER,
        NO_DEFENDER,
        NO_CAPS,
        LAND_BUSY,
        NAME_TAKEN,
        NO_WAR
    }

    public record StartOutcome(StartResult result, Campaign campaign, Battle battle) {
    }

    

    public StartOutcome start(String attackerName, String defenderName) {
        if (attackerName.equalsIgnoreCase(defenderName)) {
            return new StartOutcome(StartResult.SAME_LAND, null, null);
        }
        Land attacker = landUtil.getLandByName(attackerName);
        if (attacker == null) {
            return new StartOutcome(StartResult.NO_ATTACKER, null, null);
        }
        Land defender = landUtil.getLandByName(defenderName);
        if (defender == null) {
            return new StartOutcome(StartResult.NO_DEFENDER, null, null);
        }

        Campaign campaign = getCampaign(attacker.getName(), defender.getName());
        if (campaign == null) {
            
            return createCampaign(idOf(attacker.getName(), defender.getName()),
                    attacker, defender);
        }
        return launchNextBattle(campaign);
    }

    

    public StartOutcome createNamed(String name, String attackerName, String defenderName) {
        if (attackerName.equalsIgnoreCase(defenderName)) {
            return new StartOutcome(StartResult.SAME_LAND, null, null);
        }
        if (getByName(name) != null) {
            return new StartOutcome(StartResult.NAME_TAKEN, null, null);
        }
        Land attacker = landUtil.getLandByName(attackerName);
        if (attacker == null) {
            return new StartOutcome(StartResult.NO_ATTACKER, null, null);
        }
        Land defender = landUtil.getLandByName(defenderName);
        if (defender == null) {
            return new StartOutcome(StartResult.NO_DEFENDER, null, null);
        }
        if (getCampaign(attacker.getName(), defender.getName()) != null) {
            return new StartOutcome(StartResult.NAME_TAKEN, null, null);
        }
        return createCampaign(name, attacker, defender);
    }

    
    public StartOutcome startNamed(String name) {
        Campaign campaign = getByName(name);
        if (campaign == null) {
            return new StartOutcome(StartResult.NO_WAR, null, null);
        }
        return launchNextBattle(campaign);
    }

    private StartOutcome createCampaign(String name, Land attacker, Land defender) {
        
        Campaign busy = findActiveCampaignFor(attacker.getName());
        if (busy == null) {
            busy = findActiveCampaignFor(defender.getName());
        }
        if (busy != null) {
            return new StartOutcome(StartResult.LAND_BUSY, null, null);
        }
        boolean squatter = isSquatter(defender);
        Campaign campaign = new Campaign(idOf(attacker.getName(), defender.getName()),
                name, attacker.getName(), defender.getName(), squatter);
        campaigns.put(campaign.getId(), campaign);
        byName.put(name.toLowerCase(Locale.ROOT), campaign);
        return new StartOutcome(StartResult.CAMPAIGN_CREATED, campaign, null);
    }

    private StartOutcome launchNextBattle(Campaign campaign) {
        if (campaign.isFinished()) {
            return new StartOutcome(StartResult.SERIES_FINISHED, campaign, null);
        }
        if (campaign.isBattleActive()) {
            return new StartOutcome(StartResult.BATTLE_ALREADY_ACTIVE, campaign, null);
        }
        if (!capStore.hasCaps(campaign.getDefenderLand())) {
            return new StartOutcome(StartResult.NO_CAPS, campaign, null);
        }
        Battle battle = startBattle(campaign);
        return new StartOutcome(StartResult.BATTLE_STARTED, campaign, battle);
    }

    private Campaign findActiveCampaignFor(String landName) {
        for (Campaign c : campaigns.values()) {
            if (!c.isBattleActive()) {
                continue;
            }
            if (c.getAttackerLand().equalsIgnoreCase(landName) || c.getDefenderLand().equalsIgnoreCase(landName)) {
                return c;
            }
        }
        return null;
    }

    
    public boolean isSquatter(Land defender) {
        if (landUtil.getNation(defender) != null) {
            return false;
        }
        try {
            java.sql.Timestamp created = defender.getCreatedAt();
            long createdMs = created != null ? created.getTime() : defender.getCreationTime();
            if (createdMs <= 0) {
                return false;
            }
            return (System.currentTimeMillis() - createdMs) < 30L * 86_400_000L;
        } catch (Throwable t) {
            return false;
        }
    }

    
    private Battle startBattle(Campaign campaign) {
        long now = System.currentTimeMillis();
        long duration = config.getBattleDurationSeconds() * 1000L;
        long prepEnd = now + config.getPrepSeconds() * 1000L;
        int number = campaign.getBattlesPlayed() + 1;

        Battle battle = new Battle(campaign, number, now, duration, prepEnd);

        
        battle.roster(Side.ATTACKER).putAll(landUtil.buildRoster(campaign, Side.ATTACKER));
        battle.roster(Side.DEFENDER).putAll(landUtil.buildRoster(campaign, Side.DEFENDER));

        
        for (Cap cap : capStore.getCaps(campaign.getDefenderLand())) {
            battle.getCaps().add(new CapState(cap));
        }

        
        Land defender = landUtil.getLandByName(campaign.getDefenderLand());
        battle.getWarzoneChunks().addAll(landUtil.claimedChunks(defender));

        campaign.setCurrentBattle(battle);

        ejectNonParticipants(battle);

        if (beacons != null) {
            beacons.startBattle(battle);
        }
        if (feedback != null) {
            feedback.startBattle(battle);
        }
        if (preBattle != null) {
            preBattle.begin(battle);
        }
        if (replay != null) {
            replay.onBattleStart(battle);
        }

        broadcast("&cBattle " + number + " &cof the war &e" + campaign.getAttackerLand()
                + " &cvs &e" + campaign.getDefenderLand() + " &chas begun! "
                + "Series: &f" + campaign.siegePoints(Side.ATTACKER) + "-" + campaign.siegePoints(Side.DEFENDER)
                + "&c. Caps: &f" + battle.getCaps().size()
                + "&c. Eligible: &f" + battle.roster(Side.ATTACKER).size() + " atk / "
                + battle.roster(Side.DEFENDER).size() + " def&c.");
        return battle;
    }

    
    public void ejectNonParticipants(Battle battle) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            if (loc.getWorld() == null) {
                continue;
            }
            if (!battle.isInWarzone(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                continue;
            }
            if (battle.isParticipant(p.getUniqueId())) {
                continue;
            }
            eject(p);
        }
    }

    public void eject(Player p) {
        Location target = p.getWorld().getSpawnLocation();
        p.teleport(target);
        p.sendMessage(config.getPrefix() + WarConfig.color("&cYou cannot enter an active warzone."));
    }

    
    public void finishBattle(Battle battle, Side winner) {
        if (!battle.isActive()) {
            return;
        }
        battle.setActive(false);
        battle.setWinner(winner);

        Campaign campaign = battle.getCampaign();
        campaign.incrementBattlesPlayed();
        campaign.addSiegePoint(winner);

        if (preBattle != null) {
            preBattle.clear();
        }
        if (beacons != null) {
            beacons.endBattle(battle);
        }
        if (replay != null) {
            replay.onBattleEnd(battle);
        }
        if (feedback != null) {
            feedback.endBattle(battle);
        }

        broadcast("&6" + winner.display() + " &6won battle " + battle.getNumber()
                + "! &7(score " + fmt(battle.score(Side.ATTACKER)) + " - " + fmt(battle.score(Side.DEFENDER)) + ")"
                + " &eSeries: &f" + campaign.siegePoints(Side.ATTACKER) + "-" + campaign.siegePoints(Side.DEFENDER));

        Side seriesWinner = campaign.seriesWinner(config);
        if (seriesWinner != null) {
            finishCampaign(campaign, seriesWinner);
        } else {
            broadcast("&7Run &f/war start " + campaign.getAttackerLand() + " " + campaign.getDefenderLand()
                    + " &7to begin the next battle.");
        }
        campaign.setCurrentBattle(null);
    }

    private void finishCampaign(Campaign campaign, Side winner) {
        campaign.setFinished(true);
        campaign.setWinner(winner);
        String winnerLand = campaign.warringLand(winner);
        String loserLand = campaign.warringLand(winner.other());

        StringBuilder summary = new StringBuilder();
        summary.append("&6==== WAR OVER ====\n");
        summary.append("&e").append(campaign.getAttackerLand()).append(" &7vs &e").append(campaign.getDefenderLand())
                .append("\n");
        summary.append("&7Siege points: &f").append(campaign.siegePoints(Side.ATTACKER)).append(" - ")
                .append(campaign.siegePoints(Side.DEFENDER)).append("\n");
        summary.append("&aWinner: &f").append(winnerLand).append(" (").append(winner.display()).append(")\n");

        if (winner == Side.DEFENDER) {
            summary.append("&7The attack was REPELLED. The defender may open a counter-offensive: ")
                    .append("&f/war start ").append(loserLand).append(" ").append(winnerLand);
        } else {
            summary.append("&7Set up vassalization with: &f/war vassal set ").append(loserLand).append(" ")
                    .append(winnerLand);
        }
        broadcast(summary.toString());

        plugin.getStagingManager().onCampaignEnd(campaign);
        unregister(campaign);
    }

    
    public void forceStop(Campaign campaign) {
        Battle battle = campaign.getCurrentBattle();
        if (battle != null && battle.isActive()) {
            battle.setActive(false);
            if (beacons != null) {
                beacons.endBattle(battle);
            }
            if (replay != null) {
                replay.onBattleEnd(battle);
            }
            if (feedback != null) {
                feedback.endBattle(battle);
            }
            campaign.setCurrentBattle(null);
        }
        plugin.getStagingManager().onCampaignEnd(campaign);
        unregister(campaign);
        broadcast("&7The war &e" + campaign.getAttackerLand() + " &7vs &e" + campaign.getDefenderLand()
                + " &7was stopped by an admin.");
    }

    public void shutdown() {
        for (Campaign c : new ArrayList<>(campaigns.values())) {
            Battle b = c.getCurrentBattle();
            if (b != null && b.isActive()) {
                b.setActive(false);
                if (beacons != null) {
                    beacons.endBattle(b);
                }
                if (replay != null) {
                    replay.onBattleEnd(b);
                }
                if (feedback != null) {
                    feedback.endBattle(b);
                }
            }
        }
        campaigns.clear();
        byName.clear();
    }

    
    private void unregister(Campaign campaign) {
        campaigns.remove(campaign.getId());
        byName.values().removeIf(c -> c == campaign);
    }

    

    
    public boolean isInActiveWarzone(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        String world = loc.getWorld().getName();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (Campaign c : campaigns.values()) {
            Battle b = c.getCurrentBattle();
            if (b != null && b.isActive() && b.isInWarzone(world, cx, cz)) {
                return true;
            }
        }
        return false;
    }

    
    public Battle battleAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        String world = loc.getWorld().getName();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        for (Campaign c : campaigns.values()) {
            Battle b = c.getCurrentBattle();
            if (b != null && b.isActive() && b.isInWarzone(world, cx, cz)) {
                return b;
            }
        }
        return null;
    }

    
    public Battle battleForCombatant(UUID id) {
        for (Campaign c : campaigns.values()) {
            Battle b = c.getCurrentBattle();
            if (b != null && b.isActive() && b.isParticipant(id)) {
                return b;
            }
        }
        return null;
    }

    public void broadcast(String msg) {
        for (String line : msg.split("\n")) {
            Bukkit.broadcastMessage(config.getPrefix() + WarConfig.color(line));
        }
    }

    public static String fmt(double d) {
        return String.valueOf(Math.round(d));
    }
}
