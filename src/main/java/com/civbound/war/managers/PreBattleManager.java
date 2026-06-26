package com.civbound.war.managers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PreBattleManager {

    private final CivboundWar plugin;
    private final WarConfig config;
    private final SpawnStore spawnStore;

    
    private final Set<UUID> frozen = new HashSet<>();
    
    private final Set<UUID> attackerFrozen = new HashSet<>();

    private BukkitTask countdownTask;

    public PreBattleManager(CivboundWar plugin, WarConfig config, SpawnStore spawnStore) {
        this.plugin = plugin;
        this.config = config;
        this.spawnStore = spawnStore;
    }

    public boolean isFrozen(UUID uid) {
        return frozen.contains(uid) || attackerFrozen.contains(uid);
    }

    
    public void begin(Battle battle) {
        Campaign c = battle.getCampaign();
        Location atkSpawn = spawnStore.getSpawn(c.getAttackerLand(), c.getDefenderLand(), Side.ATTACKER);
        Location defSpawn = spawnStore.getSpawn(c.getAttackerLand(), c.getDefenderLand(), Side.DEFENDER);

        
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uid = p.getUniqueId();
            Side side = battle.sideOf(uid);
            if (side == null) continue;
            frozen.add(uid);
            Location dest = side == Side.ATTACKER ? atkSpawn : defSpawn;
            if (dest != null) p.teleport(dest);
        }

        int countdown = config.getFreezeCountdownSeconds();
        int atkExtra = config.getFreezeAttackerExtraSeconds();

        broadcastToParticipants(battle, "&eBattle starts in &f" + countdown + " &eseconds! Prepare yourselves.");

        
        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int remaining = countdown;

            @Override
            public void run() {
                remaining--;
                if (remaining > 0) {
                    if (remaining <= 5 || remaining % 5 == 0) {
                        broadcastToParticipants(battle, "&e" + remaining + "...");
                    }
                    return;
                }

                
                broadcastToParticipants(battle, "&aDefenders are free! Attackers hold for &f" + atkExtra + "&a more seconds.");
                releaseDefenders(battle);

                if (atkExtra <= 0) {
                    releaseAttackers(battle);
                    countdownTask.cancel();
                    return;
                }

                
                countdownTask.cancel();
                countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                    int atkRemaining = atkExtra;

                    @Override
                    public void run() {
                        atkRemaining--;
                        if (atkRemaining > 0) {
                            if (atkRemaining <= 5 || atkRemaining % 5 == 0) {
                                broadcastToParticipants(battle, "&cAttackers release in &f" + atkRemaining + "&c...");
                            }
                            return;
                        }
                        broadcastToParticipants(battle, "&cAttackers are free! The siege begins!");
                        releaseAttackers(battle);
                        countdownTask.cancel();
                    }
                }, 20L, 20L);
            }
        }, 20L, 20L);
    }

    
    public void clear() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        frozen.clear();
        attackerFrozen.clear();
    }

    private void releaseDefenders(Battle battle) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uid = p.getUniqueId();
            if (battle.sideOf(uid) == Side.DEFENDER) {
                frozen.remove(uid);
                attackerFrozen.remove(uid);
            }
        }
        
        battle.roster(Side.DEFENDER).keySet().forEach(uid -> {
            frozen.remove(uid);
            attackerFrozen.remove(uid);
        });
    }

    private void releaseAttackers(Battle battle) {
        battle.roster(Side.ATTACKER).keySet().forEach(uid -> {
            frozen.remove(uid);
            attackerFrozen.remove(uid);
        });
        
        frozen.removeIf(uid -> battle.sideOf(uid) == Side.ATTACKER);
        attackerFrozen.clear();
    }

    private void broadcastToParticipants(Battle battle, String msg) {
        String colored = config.getPrefix() + WarConfig.color(msg);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (battle.isParticipant(p.getUniqueId())) {
                p.sendMessage(colored);
            }
        }
    }
}
