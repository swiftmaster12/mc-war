package com.civbound.war.managers;

import me.angeschossen.lands.api.land.Land;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.logging.Level;

public final class ReplayManager {

    private final CivboundWar plugin;
    private final WarConfig config;
    private final LandUtil landUtil;

    public ReplayManager(CivboundWar plugin, WarConfig config, LandUtil landUtil) {
        this.plugin = plugin;
        this.config = config;
        this.landUtil = landUtil;
    }

    private boolean enabled() {
        return config.isRecordBattles() && Bukkit.getPluginManager().getPlugin("PaperFlashback") != null;
    }

    
    public void onBattleStart(Battle battle) {
        if (!enabled()) {
            return;
        }
        try {
            Land defender = landUtil.getLandByName(battle.getCampaign().getDefenderLand());
            List<ChunkPos> chunks = landUtil.claimedChunks(defender);
            if (chunks.isEmpty()) {
                return;
            }
            int pad = config.getRecordingChunkPadding();
            String world = chunks.get(0).world();
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (ChunkPos c : chunks) {
                if (!c.world().equals(world)) {
                    continue;
                }
                minX = Math.min(minX, c.x());
                minZ = Math.min(minZ, c.z());
                maxX = Math.max(maxX, c.x());
                maxZ = Math.max(maxZ, c.z());
            }
            minX -= pad;
            minZ -= pad;
            maxX += pad;
            maxZ += pad;

            String name = battle.getCampaign().getId() + "-battle" + battle.getNumber();
            battle.setRecordingName(name);

            String cmd = config.getRecorderStartCommand()
                    .replace("{minX}", String.valueOf(minX))
                    .replace("{minZ}", String.valueOf(minZ))
                    .replace("{maxX}", String.valueOf(maxX))
                    .replace("{maxZ}", String.valueOf(maxZ))
                    .replace("{world}", world)
                    .replace("{name}", name);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            plugin.getLogger().info("Started battle recording: " + name);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "PaperFlashback start failed (ignored)", t);
        }
    }

    
    public void onBattleEnd(Battle battle) {
        if (!enabled()) {
            return;
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), config.getRecorderStopCommand());
            if (battle.getRecordingName() != null) {
                plugin.getLogger().info("Stopped battle recording: " + battle.getRecordingName());
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "PaperFlashback stop failed (ignored)", t);
        }
    }
}
