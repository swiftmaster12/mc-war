package com.civbound.war;

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

public final class CapStore {

    private final CivboundWar plugin;
    private final File file;

    
    private final Map<String, List<Cap>> caps = new LinkedHashMap<>();

    public CapStore(CivboundWar plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "caps.yml");
        load();
    }

    private static String norm(String landName) {
        return landName.toLowerCase(Locale.ROOT);
    }

    public List<Cap> getCaps(String landName) {
        return caps.getOrDefault(norm(landName), new ArrayList<>());
    }

    public boolean hasCaps(String landName) {
        List<Cap> l = caps.get(norm(landName));
        return l != null && !l.isEmpty();
    }

    
    public Cap add(String landName, String world, int x, int y, int z, String tier) {
        List<Cap> list = caps.computeIfAbsent(norm(landName), k -> new ArrayList<>());
        String id = nextId(list, tier);
        Cap cap = new Cap(id, world, x, y, z, tier);
        list.add(cap);
        save();
        return cap;
    }

    public boolean remove(String landName, String id) {
        List<Cap> list = caps.get(norm(landName));
        if (list == null) {
            return false;
        }
        boolean removed = list.removeIf(c -> c.getId().equalsIgnoreCase(id));
        if (removed) {
            save();
        }
        return removed;
    }

    private static String nextId(List<Cap> existing, String tier) {
        int n = existing.size() + 1;
        String base = tier + n;
        boolean clash = true;
        while (clash) {
            clash = false;
            for (Cap c : existing) {
                if (c.getId().equalsIgnoreCase(base)) {
                    clash = true;
                    n++;
                    base = tier + n;
                    break;
                }
            }
        }
        return base;
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("caps");
        if (root == null) {
            return;
        }
        for (String landKey : root.getKeys(false)) {
            List<Cap> list = new ArrayList<>();
            List<Map<?, ?>> entries = root.getMapList(landKey);
            for (Map<?, ?> m : entries) {
                try {
                    String id = String.valueOf(m.get("id"));
                    String world = String.valueOf(m.get("world"));
                    int x = ((Number) m.get("x")).intValue();
                    int y = ((Number) m.get("y")).intValue();
                    int z = ((Number) m.get("z")).intValue();
                    Object tierObj = m.get("tier");
                    String tier = tierObj == null ? "mid" : String.valueOf(tierObj);
                    list.add(new Cap(id, world, x, y, z, tier));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipping malformed cap entry for " + landKey + ": " + ex.getMessage());
                }
            }
            caps.put(landKey.toLowerCase(Locale.ROOT), list);
        }
    }

    private void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, List<Cap>> e : caps.entrySet()) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Cap c : e.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", c.getId());
                m.put("world", c.getWorld());
                m.put("x", c.getX());
                m.put("y", c.getY());
                m.put("z", c.getZ());
                m.put("tier", c.getTier());
                entries.add(m);
            }
            cfg.set("caps." + e.getKey(), entries);
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save caps.yml", ex);
        }
    }
}
