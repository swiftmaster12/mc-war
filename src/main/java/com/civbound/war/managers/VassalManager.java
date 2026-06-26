package com.civbound.war;

import me.angeschossen.lands.api.land.Land;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

public final class VassalManager {

    private final CivboundWar plugin;
    private final WarConfig config;
    private final LandUtil landUtil;
    private final File file;

    
    private final Map<String, Vassalship> vassals = new LinkedHashMap<>();

    public VassalManager(CivboundWar plugin, WarConfig config, LandUtil landUtil) {
        this.plugin = plugin;
        this.config = config;
        this.landUtil = landUtil;
        this.file = new File(plugin.getDataFolder(), "vassals.yml");
        load();
    }

    
    public static final class Vassalship {
        final String vassal;
        final String overlord;
        final long startMillis;
        long endMillis;
        long lastChargeDay; 

        Vassalship(String vassal, String overlord, long startMillis, long endMillis, long lastChargeDay) {
            this.vassal = vassal;
            this.overlord = overlord;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.lastChargeDay = lastChargeDay;
        }

        public String getVassal() {
            return vassal;
        }

        public String getOverlord() {
            return overlord;
        }

        public long getEndMillis() {
            return endMillis;
        }
    }

    private static String norm(String land) {
        return land.toLowerCase(Locale.ROOT);
    }

    private static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    public List<Vassalship> list() {
        return new ArrayList<>(vassals.values());
    }

    
    public void set(String vassalLand, String overlordLand) {
        long now = System.currentTimeMillis();
        long end = now + (long) config.getVassalDurationDays() * 86_400_000L;
        
        vassals.put(norm(vassalLand), new Vassalship(vassalLand, overlordLand, now, end, today() - 1));
        save();
    }

    public boolean clear(String vassalLand) {
        boolean removed = vassals.remove(norm(vassalLand)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean isVassal(String land) {
        return vassals.containsKey(norm(land));
    }

    
    public void tick() {
        if (vassals.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long today = today();
        boolean dirty = false;
        List<Vassalship> snapshot = new ArrayList<>(vassals.values());
        for (Vassalship v : snapshot) {
            if (now >= v.endMillis) {
                vassals.remove(norm(v.vassal));
                dirty = true;
                broadcast("&eVassal term ended: &f" + v.vassal + " &eis no longer a vassal of &f" + v.overlord + "&e.");
                continue;
            }
            if (v.lastChargeDay < today) {
                charge(v);
                v.lastChargeDay = today;
                dirty = true;
            }
        }
        if (dirty) {
            save();
        }
    }

    private void charge(Vassalship v) {
        Land vassal = landUtil.getLandByName(v.vassal);
        Land overlord = landUtil.getLandByName(v.overlord);
        if (vassal == null || overlord == null) {
            
            return;
        }
        int chunks = landUtil.chunkCount(vassal);
        double perChunk = landUtil.perChunkUpkeep(vassal);
        double tax;
        if (perChunk >= 0) {
            tax = perChunk * config.getVassalTaxUpkeepFraction() * chunks;
        } else {
            
            tax = config.getVassalTaxPerChunk() * chunks;
        }
        if (tax <= 0) {
            return;
        }

        double balance = landUtil.getBalance(vassal);
        double actual = tax;
        if (!config.isShortfallDebt() && balance < tax) {
            actual = Math.max(0.0, balance);
        }
        if (actual <= 0 && !config.isShortfallDebt()) {
            broadcast("&7" + v.vassal + " could not pay its vassal tax to " + v.overlord + " (insufficient balance).");
            return;
        }
        landUtil.modifyBalance(vassal, -actual);
        landUtil.modifyBalance(overlord, actual);
        plugin.getLogger().info("Vassal tax: " + v.vassal + " -> " + v.overlord + " = " + actual
                + (config.isShortfallDebt() ? " (debt)" : ""));
    }

    private void broadcast(String msg) {
        Bukkit.broadcastMessage(config.getPrefix() + WarConfig.color(msg));
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("vassals");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            String vassal = s.getString("vassal", key);
            String overlord = s.getString("overlord");
            long start = s.getLong("start");
            long end = s.getLong("end");
            long lastDay = s.getLong("last-charge-day");
            if (overlord != null) {
                vassals.put(key.toLowerCase(Locale.ROOT),
                        new Vassalship(vassal, overlord, start, end, lastDay));
            }
        }
    }

    private void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Vassalship> e : vassals.entrySet()) {
            Vassalship v = e.getValue();
            String base = "vassals." + e.getKey() + ".";
            cfg.set(base + "vassal", v.vassal);
            cfg.set(base + "overlord", v.overlord);
            cfg.set(base + "start", v.startMillis);
            cfg.set(base + "end", v.endMillis);
            cfg.set(base + "last-charge-day", v.lastChargeDay);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save vassals.yml", ex);
        }
    }
}
