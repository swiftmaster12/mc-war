package com.civbound.war.tasks;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public final class BattleTask extends BukkitRunnable {

    private final CivboundWar plugin;
    private final WarManager warManager;
    private final WarConfig config;
    private FeedbackManager feedback;

    public BattleTask(CivboundWar plugin, WarManager warManager, WarConfig config) {
        this.plugin = plugin;
        this.warManager = warManager;
        this.config = config;
    }

    public void setFeedback(FeedbackManager feedback) {
        this.feedback = feedback;
    }

    @Override
    public void run() {
        if (!warManager.anyBattleActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        double tickSeconds = config.getTickIntervalSeconds();
        Server server = plugin.getServer();

        
        for (Battle battle : warManager.activeBattles()) {
            BeaconManager beacons = plugin.getBeaconManager();

            if (battle.isInPrep(now)) {
                if (beacons != null) {
                    beacons.tick(battle);
                }
                if (feedback != null) {
                    feedback.tick(battle);
                }
                continue;
            }

            for (CapState cs : battle.getCaps()) {
                processCap(battle, cs, server, tickSeconds);
            }

            if (beacons != null) {
                beacons.tick(battle);
            }
            if (feedback != null) {
                feedback.tick(battle);
            }

            if (checkWin(battle, now)) {
                continue;
            }
        }
    }

    
    private void processCap(Battle battle, CapState cs, Server server, double tickSeconds) {
        Cap cap = cs.getCap();
        Location center = cap.location(server);
        if (center == null) {
            return; 
        }
        int radius = (int) config.getCapRadius(); 
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();

        double attackerWeight = 0.0;
        double defenderWeight = 0.0;
        double attackerHolderMult = 0.0; 
        double defenderHolderMult = 0.0;
        int attackerCount = 0;
        int defenderCount = 0;

        for (Player p : server.getOnlinePlayers()) {
            if (p.getWorld() != center.getWorld()) {
                continue;
            }
            Location pl = p.getLocation();
            if (Math.abs(pl.getBlockX() - cx) > radius
                    || Math.abs(pl.getBlockY() - cy) > radius
                    || Math.abs(pl.getBlockZ() - cz) > radius) {
                continue;
            }
            UUID id = p.getUniqueId();
            Side side = battle.sideOf(id);
            if (side == null) {
                continue; 
            }
            Tier tier = battle.tierOf(id);
            double mult = tier == null ? 0.0 : tier.multiplier(config);
            if (side == Side.ATTACKER) {
                attackerCount++;
                attackerWeight += mult;
                attackerHolderMult = Math.max(attackerHolderMult, mult);
            } else {
                defenderCount++;
                defenderWeight += mult;
                defenderHolderMult = Math.max(defenderHolderMult, mult);
            }
        }

        
        
        double ratio = config.getOverpopRatio();
        double overpopMult = config.getOverpopMultiplier();
        if (attackerCount > 0 && defenderCount > 0) {
            if ((double) attackerCount / defenderCount > ratio) {
                int excess = attackerCount - (int) (defenderCount * ratio);
                attackerWeight *= (attackerCount - excess * (1.0 - overpopMult)) / attackerCount;
            } else if ((double) defenderCount / attackerCount > ratio) {
                int excess = defenderCount - (int) (attackerCount * ratio);
                defenderWeight *= (defenderCount - excess * (1.0 - overpopMult)) / defenderCount;
            }
        }

        boolean contested = attackerWeight > 0 && defenderWeight > 0 && attackerWeight == defenderWeight;
        cs.setContested(contested);

        Side dominant = null;
        if (attackerWeight > defenderWeight) {
            dominant = Side.ATTACKER;
        } else if (defenderWeight > attackerWeight) {
            dominant = Side.DEFENDER;
        }

        
        Side prevHolder = cs.getHolder();
        boolean wasContested = cs.isContested();
        if (dominant != null && dominant != cs.getHolder()) {
            cs.setPushing(dominant);
            cs.setCaptureProgress(cs.getCaptureProgress() + tickSeconds);
            if (cs.getCaptureProgress() >= config.getCapCaptureSeconds()) {
                cs.setHolder(dominant);
                cs.setCaptureProgress(0.0);
                cs.setPushing(null);
                broadcastCapFlip(battle, cs, prevHolder, dominant);
            }
        } else {
            
            cs.setPushing(null);
            if (cs.getCaptureProgress() > 0) {
                cs.setCaptureProgress(cs.getCaptureProgress() - tickSeconds);
            }
        }
        
        if (cs.isContested() != wasContested) {
            broadcastContested(battle, cs);
        }

        
        Side holder = cs.getHolder();
        if (holder != null) {
            double presentMult = holder == Side.ATTACKER ? attackerHolderMult : defenderHolderMult;
            double scoreMult = presentMult > 0 ? presentMult : config.getIdleHoldMultiplier();
            double points = cap.value(holder, config) * scoreMult * tickSeconds;
            if (points > 0) {
                battle.addScore(holder, points);
            }
        }
    }

    private void broadcastCapFlip(Battle battle, CapState cs, Side prev, Side newHolder) {
        String capId = cs.getCap().getId();
        String tier = cs.getCap().getTier();
        String newStr = newHolder == Side.ATTACKER ? "&cAttackers" : "&aDefenders";
        String prevStr = prev == null ? "&7neutral" : (prev == Side.ATTACKER ? "&cAttackers" : "&aDefenders");
        String msg = "&e" + capId + " &7[" + tier + "] &rcaptured by " + newStr + " &7(was " + prevStr + ") "
                + "| Score: &c" + Math.round(battle.score(Side.ATTACKER)) + " &7- &a" + Math.round(battle.score(Side.DEFENDER));
        broadcastToParticipants(battle, msg);
    }

    private void broadcastContested(Battle battle, CapState cs) {
        String capId = cs.getCap().getId();
        if (cs.isContested()) {
            broadcastToParticipants(battle, "&d" + capId + " &7is now &dCONTESTED&7!");
        }
    }

    private void broadcastToParticipants(Battle battle, String msg) {
        String colored = config.getPrefix() + WarConfig.color(msg);
        for (org.bukkit.entity.Player p : plugin.getServer().getOnlinePlayers()) {
            if (battle.isParticipant(p.getUniqueId())) {
                p.sendMessage(colored);
            }
        }
    }

    
    private boolean checkWin(Battle battle, long now) {
        double atk = battle.score(Side.ATTACKER);
        double def = battle.score(Side.DEFENDER);

        double mercy = config.getMercyLead();
        if (mercy > 0) {
            if (atk - def >= mercy) {
                warManager.finishBattle(battle, Side.ATTACKER);
                return true;
            }
            if (def - atk >= mercy) {
                warManager.finishBattle(battle, Side.DEFENDER);
                return true;
            }
        }

        if (battle.isTimedOut(now)) {
            
            Side winner;
            if (atk > def && atk >= config.getAttackerMinPoints()) {
                winner = Side.ATTACKER;
            } else {
                winner = Side.DEFENDER;
            }
            warManager.finishBattle(battle, winner);
            return true;
        }
        return false;
    }
}
