package com.civbound.war;

import me.angeschossen.lands.api.land.Land;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class StagingManager {

    private final CivboundWar plugin;
    private final WarConfig config;
    private final LandUtil landUtil;

    
    private final Map<String, Staging> staging = new LinkedHashMap<>();

    public StagingManager(CivboundWar plugin, WarConfig config, LandUtil landUtil) {
        this.plugin = plugin;
        this.config = config;
        this.landUtil = landUtil;
    }

    
    public static final class Staging {
        private final String attackerLand;
        private final String stagingLand;
        private final List<UUID> trusted = new ArrayList<>();

        Staging(String attackerLand, String stagingLand) {
            this.attackerLand = attackerLand;
            this.stagingLand = stagingLand;
        }

        public String getAttackerLand() {
            return attackerLand;
        }

        public String getStagingLand() {
            return stagingLand;
        }

        public List<UUID> getTrusted() {
            return trusted;
        }
    }

    public enum Result {
        OK,
        NO_CAMPAIGN,
        NO_STAGING_LAND,
        STAGING_IS_DEFENDER,
        TOO_CLOSE,
        NOT_LINKED
    }

    
    public record Outcome(Result result, int trustedCount, int minDistanceChunks) {
    }

    

    public Outcome set(Campaign campaign, String stagingLandName) {
        if (campaign == null) {
            return new Outcome(Result.NO_CAMPAIGN, 0, config.getStagingMinDistanceChunks());
        }
        Land stagingLand = landUtil.getLandByName(stagingLandName);
        if (stagingLand == null) {
            return new Outcome(Result.NO_STAGING_LAND, 0, config.getStagingMinDistanceChunks());
        }
        
        if (stagingLand.getName().equalsIgnoreCase(campaign.getDefenderLand())) {
            return new Outcome(Result.STAGING_IS_DEFENDER, 0, config.getStagingMinDistanceChunks());
        }
        
        Land defender = landUtil.getLandByName(campaign.getDefenderLand());
        int min = config.getStagingMinDistanceChunks();
        if (defender != null && !farEnough(stagingLand, defender, min)) {
            return new Outcome(Result.TOO_CLOSE, 0, min);
        }

        Staging record = staging.computeIfAbsent(
                campaign.getAttackerLand().toLowerCase(java.util.Locale.ROOT),
                k -> new Staging(campaign.getAttackerLand(), stagingLand.getName()));

        
        Map<UUID, Tier> roster = landUtil.buildRoster(campaign, Side.ATTACKER);
        int trusted = 0;
        for (UUID id : roster.keySet()) {
            try {
                
                boolean added = stagingLand.trustPlayer(id);
                if (added && !record.getTrusted().contains(id)) {
                    record.getTrusted().add(id);
                }
                trusted++;
            } catch (Throwable ignored) {
                
            }
        }
        return new Outcome(Result.OK, trusted, min);
    }

    
    public Result clear(String attackerLand) {
        if (attackerLand == null) {
            return Result.NOT_LINKED;
        }
        Staging record = staging.remove(attackerLand.toLowerCase(java.util.Locale.ROOT));
        if (record == null) {
            return Result.NOT_LINKED;
        }
        untrustAll(record);
        return Result.OK;
    }

    
    public void onCampaignEnd(Campaign campaign) {
        if (campaign == null || !config.isStagingAutoCleanup()) {
            return;
        }
        
        clear(campaign.getAttackerLand());
    }

    private void untrustAll(Staging record) {
        Land stagingLand = landUtil.getLandByName(record.getStagingLand());
        if (stagingLand == null) {
            return;
        }
        for (UUID id : record.getTrusted()) {
            try {
                stagingLand.untrustPlayer(id);
            } catch (Throwable ignored) {
                
            }
        }
        record.getTrusted().clear();
    }

    

    private boolean farEnough(Land staging, Land defender, int min) {
        List<ChunkPos> sc = landUtil.claimedChunks(staging);
        List<ChunkPos> dc = landUtil.claimedChunks(defender);
        if (sc.isEmpty() || dc.isEmpty()) {
            return true;
        }
        for (ChunkPos s : sc) {
            for (ChunkPos d : dc) {
                if (!s.world().equals(d.world())) {
                    continue; 
                }
                int dx = Math.abs(s.x() - d.x());
                int dz = Math.abs(s.z() - d.z());
                int cheb = Math.max(dx, dz);
                if (cheb < min) {
                    return false;
                }
            }
        }
        return true;
    }

    public Set<String> linkedAttackerLands() {
        return staging.keySet();
    }
}
