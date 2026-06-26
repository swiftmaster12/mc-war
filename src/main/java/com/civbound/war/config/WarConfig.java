package com.civbound.war.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class WarConfig {

    
    private int siegePointsToWin;
    private int squatterSiegePointsToWin;

    
    private long battleDurationSeconds;
    private long prepSeconds;
    private long tickIntervalTicks;
    private double mercyLead;
    private double attackerMinPoints;

    
    private double capRadius;
    private double capCaptureSeconds;
    private double idleHoldMultiplier;
    private double coreAtk;
    private double coreDef;
    private double midAtk;
    private double midDef;
    private double exteriorAtk;
    private double exteriorDef;

    
    private double tierCitizenMultiplier;
    private double tierNationMultiplier;
    private double tierAllyMultiplier;

    
    private int maxAlliesPerSide;

    
    private int eligibilityMinDaysInLand;
    private double eligibilityMinPlaytimeHours;
    private boolean unknownTrustEligible;

    
    private Set<Material> attackerPlaceAllow;
    private boolean attackerBreakDenyList;
    private Set<Material> attackerBreakAllow;

    
    private boolean defenderPlaceAllowAll;
    private Set<Material> defenderPlaceAllow;
    private boolean defenderBreakAllowAll;
    private boolean defenderBreakDenyList;
    private Set<Material> defenderBreakAllow;
    private boolean defenderContainerAccess;

    
    private boolean allowExplosions;

    
    private String maxGearTier;
    private Set<Material> allowedGearMaterials;
    private Set<String> allowedExtraArmor;
    private boolean banEnderPearls;

    
    private double vassalTaxUpkeepFraction;
    private int vassalDurationDays;
    private long vassalTaxCheckIntervalSeconds;
    private double vassalTaxPerChunk;
    private boolean shortfallDebt;

    
    private String ejectTo;
    private boolean bossBarEnabled;
    private boolean particlesEnabled;
    private boolean recordBattles;
    private int recordingChunkPadding;
    private String recorderStartCommand;
    private String recorderStopCommand;

    private String prefix;

    
    private int stagingMinDistanceChunks;
    private boolean stagingAutoCleanup;

    
    private boolean beaconEnabled;
    private Material beaconBaseBlock;
    private String beamColorNeutral;
    private String beamColorAttacker;
    private String beamColorDefender;
    private String beamColorContested;
    private int beaconPreviewSeconds;

    

    
    private double overpopRatio;
    private double overpopMultiplier;

    
    private int freezeCountdownSeconds;
    private int freezeAttackerExtraSeconds;

    public void load(FileConfiguration cfg, Logger logger) {
        siegePointsToWin = Math.max(1, cfg.getInt("siege-points-to-win", 2));
        squatterSiegePointsToWin = Math.max(1, cfg.getInt("squatter-siege-points-to-win", 1));

        battleDurationSeconds = Math.max(1L, cfg.getLong("battle-duration-seconds", 3600L));
        prepSeconds = Math.max(0L, cfg.getLong("prep-seconds", 0L));
        tickIntervalTicks = Math.max(1L, cfg.getLong("tick-interval-ticks", 20L));
        mercyLead = Math.max(0.0, cfg.getDouble("mercy-lead", 450.0));
        attackerMinPoints = Math.max(0.0, cfg.getDouble("attacker-min-points", 1.0));

        capRadius = Math.max(0.5, cfg.getDouble("cap-radius", 6.0));
        capCaptureSeconds = Math.max(0.1, cfg.getDouble("cap-capture-seconds", 20.0));
        idleHoldMultiplier = Math.max(0.0, cfg.getDouble("idle-hold-multiplier", 1.0));
        coreAtk = cfg.getDouble("cap-tiers.core.attacker-value", 10.0);
        coreDef = cfg.getDouble("cap-tiers.core.defender-value", 1.0);
        midAtk = cfg.getDouble("cap-tiers.mid.attacker-value", 5.0);
        midDef = cfg.getDouble("cap-tiers.mid.defender-value", 5.0);
        exteriorAtk = cfg.getDouble("cap-tiers.exterior.attacker-value", 1.0);
        exteriorDef = cfg.getDouble("cap-tiers.exterior.defender-value", 10.0);

        tierCitizenMultiplier = cfg.getDouble("tier-citizen-multiplier", 1.0);
        tierNationMultiplier = cfg.getDouble("tier-nation-multiplier", 0.75);
        tierAllyMultiplier = cfg.getDouble("tier-ally-multiplier", 0.5);

        maxAlliesPerSide = Math.max(0, cfg.getInt("max-allies-per-side", 2));

        eligibilityMinDaysInLand = Math.max(0, cfg.getInt("eligibility-min-days-in-land", 7));
        eligibilityMinPlaytimeHours = Math.max(0.0, cfg.getDouble("eligibility-min-playtime-hours", 8.0));
        unknownTrustEligible = cfg.getBoolean("unknown-trust-eligible", false);

        attackerPlaceAllow = materials(cfg.getStringList("attacker-place-allow"), logger, "attacker-place-allow");
        attackerBreakDenyList = !"allow-list".equalsIgnoreCase(cfg.getString("attacker-break-mode", "deny-list"));
        attackerBreakAllow = materials(cfg.getStringList("attacker-break-allow"), logger, "attacker-break-allow");

        String defPlaceMode = cfg.getString("defender-place-mode", "allow-all").trim().toLowerCase(Locale.ROOT);
        defenderPlaceAllowAll = defPlaceMode.equals("allow-all");
        defenderPlaceAllow = materials(cfg.getStringList("defender-place-allow"), logger, "defender-place-allow");

        String defBreakMode = cfg.getString("defender-break-mode", "allow-all").trim().toLowerCase(Locale.ROOT);
        defenderBreakAllowAll = defBreakMode.equals("allow-all");
        defenderBreakDenyList = defBreakMode.equals("deny-list");
        defenderBreakAllow = materials(cfg.getStringList("defender-break-allow"), logger, "defender-break-allow");

        defenderContainerAccess = cfg.getBoolean("defender-container-access", true);

        allowExplosions = cfg.getBoolean("allow-explosions", true);

        maxGearTier = cfg.getString("max-gear-tier", "IRON");
        allowedGearMaterials = materials(cfg.getStringList("allowed-gear-materials"), logger, "allowed-gear-materials");
        allowedExtraArmor = new HashSet<>();
        for (String s : cfg.getStringList("allowed-extra-armor")) {
            if (s != null && !s.isBlank()) {
                allowedExtraArmor.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        banEnderPearls = cfg.getBoolean("ban-ender-pearls", true);

        vassalTaxUpkeepFraction = Math.max(0.0, cfg.getDouble("vassal-tax-upkeep-fraction", 0.5));
        vassalDurationDays = Math.max(1, cfg.getInt("vassal-duration-days", 60));
        vassalTaxCheckIntervalSeconds = Math.max(60L, cfg.getLong("vassal-tax-check-interval-seconds", 3600L));
        vassalTaxPerChunk = Math.max(0.0, cfg.getDouble("vassal-tax-per-chunk", 1.0));
        shortfallDebt = "debt".equalsIgnoreCase(cfg.getString("tax-shortfall-mode", "partial"));

        ejectTo = cfg.getString("eject-to", "spawn");
        bossBarEnabled = cfg.getBoolean("boss-bar-enabled", true);
        particlesEnabled = cfg.getBoolean("particles-enabled", true);
        recordBattles = cfg.getBoolean("record-battles", true);
        recordingChunkPadding = Math.max(0, cfg.getInt("recording-chunk-padding", 3));
        recorderStartCommand = cfg.getString("recorder-start-command",
                "paperflashback start chunks from {minX} {minZ} to {maxX} {maxZ} in {world} named {name}");
        recorderStopCommand = cfg.getString("recorder-stop-command", "paperflashback stop all save=true");

        prefix = color(cfg.getString("messages.prefix", "&c[War] &r"));

        stagingMinDistanceChunks = Math.max(0, cfg.getInt("staging-min-distance-chunks", 3));
        stagingAutoCleanup = cfg.getBoolean("staging-auto-cleanup", true);

        beaconEnabled = cfg.getBoolean("beacon.enabled", true);
        beaconBaseBlock = matchBlock(cfg.getString("beacon.base-block", "IRON_BLOCK"), logger,
                "beacon.base-block", Material.IRON_BLOCK);
        beamColorNeutral = cfg.getString("beacon.beam-colors.neutral", "WHITE");
        beamColorAttacker = cfg.getString("beacon.beam-colors.attacker", "RED");
        beamColorDefender = cfg.getString("beacon.beam-colors.defender", "LIME");
        beamColorContested = cfg.getString("beacon.beam-colors.contested", "PURPLE");
        beaconPreviewSeconds = Math.max(1, cfg.getInt("beacon.preview-seconds", 60));

        overpopRatio = Math.max(1.0, cfg.getDouble("overpop-ratio", 2.0));
        overpopMultiplier = Math.max(0.0, Math.min(1.0, cfg.getDouble("overpop-multiplier", 0.25)));

        freezeCountdownSeconds = Math.max(0, cfg.getInt("freeze-countdown-seconds", 15));
        freezeAttackerExtraSeconds = Math.max(0, cfg.getInt("freeze-attacker-extra-seconds", 45));
    }

    private static Material matchBlock(String name, Logger logger, String key, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material mat = Material.matchMaterial(name.trim());
        if (mat == null || !mat.isBlock()) {
            logger.warning("Invalid block in " + key + ": " + name + " (using " + fallback + ")");
            return fallback;
        }
        return mat;
    }

    
    public static Material stainedGlass(String colorName) {
        if (colorName != null && !colorName.isBlank()) {
            Material m = Material.matchMaterial(colorName.trim().toUpperCase(Locale.ROOT) + "_STAINED_GLASS");
            if (m != null) {
                return m;
            }
        }
        return Material.WHITE_STAINED_GLASS;
    }

    private static Set<Material> materials(List<String> raw, Logger logger, String key) {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : raw) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Material mat = Material.matchMaterial(name.trim());
            if (mat == null) {
                logger.warning("Unknown material in " + key + ": " + name);
                continue;
            }
            set.add(mat);
        }
        return set;
    }

    
    public static String color(String s) {
        return s == null ? "" : s.replace('&', '§');
    }

    
    public double capValue(String tier, Side side) {
        boolean atk = side == Side.ATTACKER;
        return switch (tier == null ? "mid" : tier.toLowerCase(Locale.ROOT)) {
            case "core" -> atk ? coreAtk : coreDef;
            case "exterior" -> atk ? exteriorAtk : exteriorDef;
            default -> atk ? midAtk : midDef;
        };
    }

    public boolean isValidTier(String tier) {
        if (tier == null) {
            return false;
        }
        String t = tier.toLowerCase(Locale.ROOT);
        return t.equals("core") || t.equals("mid") || t.equals("exterior");
    }

    public int getSiegePointsToWin() {
        return siegePointsToWin;
    }

    public int getSquatterSiegePointsToWin() {
        return squatterSiegePointsToWin;
    }

    public long getBattleDurationSeconds() {
        return battleDurationSeconds;
    }

    public long getPrepSeconds() {
        return prepSeconds;
    }

    public long getTickIntervalTicks() {
        return tickIntervalTicks;
    }

    public double getTickIntervalSeconds() {
        return tickIntervalTicks / 20.0;
    }

    public double getMercyLead() {
        return mercyLead;
    }

    public double getAttackerMinPoints() {
        return attackerMinPoints;
    }

    public double getCapRadius() {
        return capRadius;
    }

    public double getCapCaptureSeconds() {
        return capCaptureSeconds;
    }

    public double getIdleHoldMultiplier() {
        return idleHoldMultiplier;
    }

    public double getTierCitizenMultiplier() {
        return tierCitizenMultiplier;
    }

    public double getTierNationMultiplier() {
        return tierNationMultiplier;
    }

    public double getTierAllyMultiplier() {
        return tierAllyMultiplier;
    }

    public int getMaxAlliesPerSide() {
        return maxAlliesPerSide;
    }

    public int getEligibilityMinDaysInLand() {
        return eligibilityMinDaysInLand;
    }

    public double getEligibilityMinPlaytimeHours() {
        return eligibilityMinPlaytimeHours;
    }

    public boolean isUnknownTrustEligible() {
        return unknownTrustEligible;
    }

    public Set<Material> getAttackerPlaceAllow() {
        return attackerPlaceAllow;
    }

    public boolean isAttackerBreakDenyList() {
        return attackerBreakDenyList;
    }

    public Set<Material> getAttackerBreakAllow() {
        return attackerBreakAllow;
    }

    public boolean isDefenderPlaceAllowAll() { return defenderPlaceAllowAll; }
    public Set<Material> getDefenderPlaceAllow() { return defenderPlaceAllow; }
    public boolean isDefenderBreakAllowAll() { return defenderBreakAllowAll; }
    public boolean isDefenderBreakDenyList() { return defenderBreakDenyList; }
    public Set<Material> getDefenderBreakAllow() { return defenderBreakAllow; }
    public boolean isDefenderContainerAccess() { return defenderContainerAccess; }
    public boolean isAllowExplosions() { return allowExplosions; }

    public String getMaxGearLabel() {
        return maxGearTier;
    }

    public Set<Material> getAllowedGearMaterials() {
        return allowedGearMaterials;
    }

    public Set<String> getAllowedExtraArmor() {
        return allowedExtraArmor;
    }

    public boolean isBanEnderPearls() {
        return banEnderPearls;
    }

    public double getVassalTaxUpkeepFraction() {
        return vassalTaxUpkeepFraction;
    }

    public int getVassalDurationDays() {
        return vassalDurationDays;
    }

    public long getVassalTaxCheckIntervalSeconds() {
        return vassalTaxCheckIntervalSeconds;
    }

    public double getVassalTaxPerChunk() {
        return vassalTaxPerChunk;
    }

    public boolean isShortfallDebt() {
        return shortfallDebt;
    }

    public String getEjectTo() {
        return ejectTo;
    }

    public boolean isBossBarEnabled() {
        return bossBarEnabled;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public boolean isRecordBattles() {
        return recordBattles;
    }

    public int getRecordingChunkPadding() {
        return recordingChunkPadding;
    }

    public String getRecorderStartCommand() {
        return recorderStartCommand;
    }

    public String getRecorderStopCommand() {
        return recorderStopCommand;
    }

    public int getStagingMinDistanceChunks() {
        return stagingMinDistanceChunks;
    }

    public boolean isStagingAutoCleanup() {
        return stagingAutoCleanup;
    }

    public boolean isBeaconEnabled() {
        return beaconEnabled;
    }

    public Material getBeaconBaseBlock() {
        return beaconBaseBlock;
    }

    
    public Material beamGlass(Side holder, boolean contested) {
        String color;
        if (contested) {
            color = beamColorContested;
        } else if (holder == Side.ATTACKER) {
            color = beamColorAttacker;
        } else if (holder == Side.DEFENDER) {
            color = beamColorDefender;
        } else {
            color = beamColorNeutral;
        }
        return stainedGlass(color);
    }

    public int getBeaconPreviewSeconds() {
        return beaconPreviewSeconds;
    }

    public double getOverpopRatio() { return overpopRatio; }
    public double getOverpopMultiplier() { return overpopMultiplier; }
    public int getFreezeCountdownSeconds() { return freezeCountdownSeconds; }
    public int getFreezeAttackerExtraSeconds() { return freezeAttackerExtraSeconds; }

    public String getPrefix() {
        return prefix;
    }
}
