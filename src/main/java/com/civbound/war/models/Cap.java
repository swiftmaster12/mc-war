package com.civbound.war;

import org.bukkit.Location;
import org.bukkit.World;

public final class Cap {

    private final String id;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final String tier;

    public Cap(String id, String world, int x, int y, int z, String tier) {
        this.id = id;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tier = tier;
    }

    public String getId() {
        return id;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getTier() {
        return tier;
    }

    

    public boolean inCapCube(int bx, int by, int bz, int radius) {
        return Math.abs(bx - x) <= radius
                && Math.abs(by - y) <= radius
                && Math.abs(bz - z) <= radius;
    }

    

    public boolean inCapShell(int bx, int by, int bz, int radius) {
        return inCapCube(bx, by, bz, radius + 1) && !inCapCube(bx, by, bz, radius);
    }

    
    public double value(Side side, WarConfig config) {
        return config.capValue(tier, side);
    }

    
    public Location location(org.bukkit.Server server) {
        World w = server.getWorld(world);
        if (w == null) {
            return null;
        }
        return new Location(w, x + 0.5, y, z + 0.5);
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    @Override
    public String toString() {
        return id + " (" + tier + ") @ " + world + " [" + x + "," + y + "," + z + "]";
    }
}
