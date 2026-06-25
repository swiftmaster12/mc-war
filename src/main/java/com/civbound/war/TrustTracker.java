package com.civbound.war;

import me.angeschossen.lands.api.events.LandTrustPlayerEvent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class TrustTracker implements Listener {

    private final CivboundWar plugin;
    private final File file;

    
    private final Map<String, Long> trustedSince = new HashMap<>();

    public TrustTracker(CivboundWar plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trust.yml");
        load();
    }

    private static String key(String landName, UUID uuid) {
        return landName.toLowerCase(java.util.Locale.ROOT) + "|" + uuid;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrust(LandTrustPlayerEvent event) {
        if (event.getLand() == null || event.getTargetUID() == null) {
            return;
        }
        String k = key(event.getLand().getName(), event.getTargetUID());
        
        trustedSince.putIfAbsent(k, System.currentTimeMillis());
        save();
    }

    
    public Long trustedSince(String landName, UUID uuid) {
        return trustedSince.get(key(landName, uuid));
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String k : cfg.getKeys(false)) {
            trustedSince.put(k, cfg.getLong(k));
        }
    }

    private void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Long> e : trustedSince.entrySet()) {
            cfg.set(e.getKey(), e.getValue());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save trust.yml", ex);
        }
    }
}
