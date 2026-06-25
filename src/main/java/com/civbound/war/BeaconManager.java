package com.civbound.war;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BeaconManager {

    

    public static volatile boolean IS_PLUGIN_PLACING = false;

    private final CivboundWar plugin;
    private final WarConfig config;
    private final Server server;

    
    private final Map<Battle, List<CapBeacon>> battleSites = new HashMap<>();
    
    private final Map<String, Preview> previews = new LinkedHashMap<>();

    public BeaconManager(CivboundWar plugin, WarConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.server = plugin.getServer();
    }

    
    private record SavedBlock(Block block, BlockData original) {
    }

    
    private static final class CapBeacon {
        private final CapState capState; 
        private Block glassBlock;
        private final List<SavedBlock> saved = new ArrayList<>();
        private Material lastGlass;

        CapBeacon(CapState capState) {
            this.capState = capState;
        }
    }

    private static final class Preview {
        private final List<CapBeacon> sites = new ArrayList<>();
        private BukkitTask expiry;
    }

    

    
    public void startBattle(Battle battle) {
        if (!config.isBeaconEnabled()) {
            return;
        }
        List<CapBeacon> sites = new ArrayList<>();
        for (CapState cs : battle.getCaps()) {
            CapBeacon b = erect(cs.getCap(), cs);
            if (b != null) {
                sites.add(b);
            }
        }
        if (!sites.isEmpty()) {
            battleSites.put(battle, sites);
        }
    }

    
    public void tick(Battle battle) {
        List<CapBeacon> sites = battleSites.get(battle);
        if (sites == null) {
            return;
        }
        for (CapBeacon b : sites) {
            if (b.capState == null) {
                continue;
            }
            recolor(b, config.beamGlass(b.capState.getHolder(), b.capState.isContested()));
        }
    }

    
    public void endBattle(Battle battle) {
        List<CapBeacon> sites = battleSites.remove(battle);
        if (sites != null) {
            restoreAll(sites);
        }
    }

    

    

    public int togglePreview(String warName, String defenderLand) {
        String key = warName.toLowerCase(Locale.ROOT);
        Preview existing = previews.remove(key);
        if (existing != null) {
            if (existing.expiry != null) {
                existing.expiry.cancel();
            }
            restoreAll(existing.sites);
            return -1;
        }
        List<Cap> caps = plugin.getCapStore().getCaps(defenderLand);
        if (caps.isEmpty()) {
            return -2;
        }
        Preview preview = new Preview();
        for (Cap cap : caps) {
            CapBeacon b = erect(cap, null);
            if (b != null) {
                
                recolor(b, config.beamGlass(null, false));
                preview.sites.add(b);
            }
        }
        if (preview.sites.isEmpty()) {
            return -2;
        }
        long ticks = config.getBeaconPreviewSeconds() * 20L;
        preview.expiry = server.getScheduler().runTaskLater(plugin, () -> {
            Preview p = previews.remove(key);
            if (p != null) {
                restoreAll(p.sites);
            }
        }, ticks);
        previews.put(key, preview);
        return preview.sites.size();
    }

    
    public void clear() {
        for (List<CapBeacon> sites : battleSites.values()) {
            restoreAll(sites);
        }
        battleSites.clear();
        for (Preview p : previews.values()) {
            if (p.expiry != null) {
                p.expiry.cancel();
            }
            restoreAll(p.sites);
        }
        previews.clear();
    }

    

    private CapBeacon erect(Cap cap, CapState capState) {
        World world = server.getWorld(cap.getWorld());
        if (world == null) {
            return null;
        }
        CapBeacon b = new CapBeacon(capState);
        try {
            
            Material base = config.getBeaconBaseBlock();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    overwrite(b, world.getBlockAt(cap.getX() + dx, cap.getY() - 1, cap.getZ() + dz), base);
                }
            }
            
            overwrite(b, world.getBlockAt(cap.getX(), cap.getY(), cap.getZ()), Material.BEACON);
            
            Block glass = world.getBlockAt(cap.getX(), cap.getY() + 1, cap.getZ());
            overwrite(b, glass, Material.WHITE_STAINED_GLASS);
            b.glassBlock = glass;
            b.lastGlass = Material.WHITE_STAINED_GLASS;
        } catch (Throwable t) {
            
            restore(b);
            return null;
        }
        return b;
    }

    private void overwrite(CapBeacon b, Block block, Material material) {
        b.saved.add(new SavedBlock(block, block.getBlockData().clone()));
        IS_PLUGIN_PLACING = true;
        try {
            block.setType(material, false);
        } finally {
            IS_PLUGIN_PLACING = false;
        }
    }

    private void recolor(CapBeacon b, Material glass) {
        if (b.glassBlock == null || glass == b.lastGlass) {
            return;
        }
        try {
            IS_PLUGIN_PLACING = true;
            b.glassBlock.setType(glass, false);
            b.lastGlass = glass;
        } catch (Throwable ignored) {
            
        } finally {
            IS_PLUGIN_PLACING = false;
        }
    }

    private void restoreAll(List<CapBeacon> sites) {
        for (CapBeacon b : sites) {
            restore(b);
        }
    }

    private void restore(CapBeacon b) {
        
        IS_PLUGIN_PLACING = true;
        try {
            for (int i = b.saved.size() - 1; i >= 0; i--) {
                SavedBlock sb = b.saved.get(i);
                try {
                    sb.block().setBlockData(sb.original(), false);
                } catch (Throwable ignored) {
                    
                }
            }
        } finally {
            IS_PLUGIN_PLACING = false;
        }
        b.saved.clear();
    }
}
