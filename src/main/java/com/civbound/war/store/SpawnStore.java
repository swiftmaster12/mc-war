package com.civbound.war.store;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class SpawnStore {

    private final File file;
    private YamlConfiguration cfg;

    public SpawnStore(JavaPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "war-spawns.yml");
        reload();
    }

    public void setSpawn(String attackerLand, String defenderLand, Side side, Location loc) {
        String key = key(attackerLand, defenderLand) + "." + side.name().toLowerCase(Locale.ROOT);
        cfg.set(key, serialize(loc));
        save();
    }

    public Location getSpawn(String attackerLand, String defenderLand, Side side) {
        String key = key(attackerLand, defenderLand) + "." + side.name().toLowerCase(Locale.ROOT);
        String raw = cfg.getString(key);
        return raw == null ? null : deserialize(raw);
    }

    public void clearSpawns(String attackerLand, String defenderLand) {
        cfg.set(key(attackerLand, defenderLand), null);
        save();
    }

    private String key(String atk, String def) {
        return (atk + "_vs_" + def).toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String serialize(Location loc) {
        return loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + ","
                + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch();
    }

    private static Location deserialize(String s) {
        try {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w,
                    Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (Exception e) {
            return null;
        }
    }

    private void reload() {
        cfg = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
    }

    private void save() {
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}
