package com.civbound.war.util;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.ChunkCoordinate;
import me.angeschossen.lands.api.land.Land;
import me.angeschossen.lands.api.nation.Nation;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LandUtil {

    private static final long DAY_MILLIS = 86_400_000L;

    private final LandsIntegration lands;
    private final WarConfig config;
    private final TrustTracker trust;

    public LandUtil(LandsIntegration lands, WarConfig config, TrustTracker trust) {
        this.lands = lands;
        this.config = config;
        this.trust = trust;
    }

    public Land getLandByName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return lands.getLandByName(name);
        } catch (Throwable t) {
            return null;
        }
    }

    public Collection<Land> allLands() {
        try {
            return lands.getLands();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public Land landAt(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        try {
            var area = lands.getArea(loc);
            return area == null ? null : area.getLand();
        } catch (Throwable t) {
            return null;
        }
    }

    
    public List<ChunkPos> claimedChunks(Land land) {
        List<ChunkPos> out = new ArrayList<>();
        if (land == null) {
            return out;
        }
        for (World world : Bukkit.getWorlds()) {
            Collection<ChunkCoordinate> chunks;
            try {
                chunks = land.getChunks(world);
            } catch (Throwable t) {
                continue;
            }
            if (chunks == null) {
                continue;
            }
            for (ChunkCoordinate cc : chunks) {
                out.add(new ChunkPos(world.getName(), cc.getX(), cc.getZ()));
            }
        }
        return out;
    }

    public int chunkCount(Land land) {
        if (land == null) {
            return 0;
        }
        int n = claimedChunks(land).size();
        if (n == 0) {
            try {
                n = Math.max(0, land.getSize());
            } catch (Throwable ignored) {
            }
        }
        return n;
    }

    

    public Set<UUID> members(Land land) {
        Set<UUID> out = new LinkedHashSet<>();
        if (land == null) {
            return out;
        }
        try {
            UUID owner = land.getOwnerUID();
            if (owner != null) {
                out.add(owner);
            }
        } catch (Throwable ignored) {
        }
        try {
            Collection<UUID> trusted = land.getTrustedPlayers();
            if (trusted != null) {
                out.addAll(trusted);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public Nation getNation(Land land) {
        if (land == null) {
            return null;
        }
        try {
            return land.getNation();
        } catch (Throwable t) {
            return null;
        }
    }

    
    public Collection<Land> nationLandsExcluding(Land land) {
        Nation nation = getNation(land);
        if (nation == null) {
            return Collections.emptyList();
        }
        List<Land> out = new ArrayList<>();
        try {
            for (Land l : nation.getLands()) {
                if (l != null && !l.getName().equalsIgnoreCase(land.getName())) {
                    out.add(l);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    
    public boolean isFormalAlly(Land warring, Land ally) {
        if (warring == null || ally == null) {
            return false;
        }
        try {
            return warring.isAlly(ally);
        } catch (Throwable t) {
            return false;
        }
    }

    
    public double perChunkUpkeep(Land land) {
        if (land == null) {
            return -1;
        }
        try {
            double total = land.getUpkeepCosts();
            int chunks = chunkCount(land);
            if (chunks <= 0 || total < 0 || Double.isNaN(total)) {
                return -1;
            }
            return total / chunks;
        } catch (Throwable t) {
            return -1;
        }
    }

    public double getBalance(Land land) {
        try {
            return land.getBalance();
        } catch (Throwable t) {
            return 0;
        }
    }

    
    public boolean modifyBalance(Land land, double delta) {
        try {
            return land.modifyBalance(delta);
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean setBalance(Land land, double value) {
        try {
            return land.setBalance(value);
        } catch (Throwable t) {
            return false;
        }
    }

    

    public Map<UUID, Tier> buildRoster(Campaign campaign, Side side) {
        Map<UUID, Tier> roster = new LinkedHashMap<>();
        Set<UUID> seen = new LinkedHashSet<>();
        long now = System.currentTimeMillis();

        Land warring = getLandByName(campaign.warringLand(side));
        if (warring == null) {
            return roster;
        }

        
        addTier(roster, seen, warring, Tier.CITIZEN, now);

        
        for (Land nl : nationLandsExcluding(warring)) {
            addTier(roster, seen, nl, Tier.NATION, now);
        }

        
        for (String allyName : campaign.allies(side)) {
            Land ally = getLandByName(allyName);
            if (ally != null) {
                addTier(roster, seen, ally, Tier.ALLY, now);
            }
        }
        return roster;
    }

    private void addTier(Map<UUID, Tier> roster, Set<UUID> seen, Land land, Tier tier, long now) {
        for (UUID id : members(land)) {
            if (seen.contains(id)) {
                continue; 
            }
            if (eligible(id, land, now)) {
                roster.put(id, tier);
                seen.add(id); 
            }
            
            
        }
    }

    
    public boolean eligible(UUID id, Land land, long now) {
        
        double hours = playtimeHours(id);
        if (hours < config.getEligibilityMinPlaytimeHours()) {
            return false;
        }
        
        long requiredMillis = (long) config.getEligibilityMinDaysInLand() * DAY_MILLIS;
        Long since = trustedSince(id, land);
        if (since == null) {
            return config.isUnknownTrustEligible();
        }
        return (now - since) > requiredMillis;
    }

    

    private Long trustedSince(UUID id, Land land) {
        try {
            if (id.equals(land.getOwnerUID())) {
                Long created = landCreationMillis(land);
                if (created != null) {
                    return created;
                }
            }
        } catch (Throwable ignored) {
        }
        Long recorded = trust.trustedSince(land.getName(), id);
        if (recorded != null) {
            return recorded;
        }
        
        
        return landCreationMillis(land);
    }

    
    private Long landCreationMillis(Land land) {
        try {
            java.sql.Timestamp created = land.getCreatedAt();
            if (created != null) {
                return created.getTime();
            }
            long ct = land.getCreationTime();
            if (ct > 0) {
                return ct;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public double playtimeHours(UUID id) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(id);
            int ticks = op.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return ticks / 20.0 / 3600.0;
        } catch (Throwable t) {
            return 0.0;
        }
    }
}
