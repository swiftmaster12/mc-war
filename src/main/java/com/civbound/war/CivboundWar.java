package com.civbound.war;

import me.angeschossen.lands.api.LandsIntegration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CivboundWar extends JavaPlugin {

    private final WarConfig warConfig = new WarConfig();

    private LandsIntegration lands;
    private LandUtil landUtil;
    private TrustTracker trustTracker;
    private CapStore capStore;
    private SpawnStore spawnStore;
    private WarManager warManager;
    private VassalManager vassalManager;
    private StagingManager stagingManager;
    private FeedbackManager feedback;
    private ReplayManager replay;
    private BeaconManager beaconManager;
    private PreBattleManager preBattle;

    private BukkitTask battleTask;
    private BukkitTask vassalTask;

    @Override
    public void onLoad() {
        tryInitLands();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        warConfig.load(getConfig(), getLogger());

        if (lands == null && !tryInitLands()) {
            getLogger().severe("Lands integration could not be initialized. Disabling CivboundWar.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        trustTracker = new TrustTracker(this);
        landUtil = new LandUtil(lands, warConfig, trustTracker);
        capStore = new CapStore(this);
        spawnStore = new SpawnStore(this);
        preBattle = new PreBattleManager(this, warConfig, spawnStore);
        warManager = new WarManager(this, warConfig, landUtil, capStore);
        warManager.setPreBattle(preBattle);
        vassalManager = new VassalManager(this, warConfig, landUtil);
        stagingManager = new StagingManager(this, warConfig, landUtil);
        feedback = new FeedbackManager(this, warConfig);
        replay = new ReplayManager(this, warConfig, landUtil);
        beaconManager = new BeaconManager(this, warConfig);

        warManager.setFeedback(feedback);
        warManager.setReplay(replay);
        warManager.setBeacons(beaconManager);

        
        
        PluginCommand warCmd = getCommand("war");
        if (warCmd != null) {
            WarCommand executor = new WarCommand(this);
            warCmd.setExecutor(executor);
            warCmd.setTabCompleter(executor);
            try {
                CommandMap cmdMap = Bukkit.getCommandMap();
                cmdMap.register("war", "civbound", warCmd);
            } catch (Exception e) {
                getLogger().warning("Could not force-register /war: " + e.getMessage());
            }
        } else {
            getLogger().warning("Command 'war' missing from plugin.yml; commands disabled.");
        }

        
        getServer().getPluginManager().registerEvents(trustTracker, this);
        getServer().getPluginManager().registerEvents(new WarListener(this, warManager, warConfig), this);
        getServer().getPluginManager().registerEvents(
                new LandsDefenderOverride(warManager, landUtil, warConfig), this);

        
        long interval = warConfig.getTickIntervalTicks();
        BattleTask engine = new BattleTask(this, warManager, warConfig);
        engine.setFeedback(feedback);
        battleTask = engine.runTaskTimer(this, interval, interval);

        
        long vassalTicks = warConfig.getVassalTaxCheckIntervalSeconds() * 20L;
        vassalTask = getServer().getScheduler().runTaskTimer(this,
                () -> vassalManager.tick(), vassalTicks, vassalTicks);

        getLogger().info("CivboundWar v2 enabled (tick interval: " + interval + " ticks).");
    }

    @Override
    public void onDisable() {
        if (battleTask != null) {
            battleTask.cancel();
            battleTask = null;
        }
        if (vassalTask != null) {
            vassalTask.cancel();
            vassalTask = null;
        }
        if (preBattle != null) {
            preBattle.clear();
        }
        if (warManager != null) {
            warManager.shutdown();
        }
        if (beaconManager != null) {
            beaconManager.clear();
        }
        if (feedback != null) {
            feedback.clear();
        }
        getLogger().info("CivboundWar disabled.");
    }

    
    public void reloadWarConfig() {
        reloadConfig();
        warConfig.load(getConfig(), getLogger());
    }

    

    public boolean isInActiveWarzone(Location loc) {
        return warManager != null && warManager.isInActiveWarzone(loc);
    }

    public WarManager getWarManager() {
        return warManager;
    }

    public WarConfig getWarConfig() {
        return warConfig;
    }

    public LandUtil getLandUtil() {
        return landUtil;
    }

    public CapStore getCapStore() {
        return capStore;
    }

    public VassalManager getVassalManager() {
        return vassalManager;
    }

    public StagingManager getStagingManager() {
        return stagingManager;
    }

    public FeedbackManager getFeedback() {
        return feedback;
    }

    public BeaconManager getBeaconManager() {
        return beaconManager;
    }

    public SpawnStore getSpawnStore() {
        return spawnStore;
    }

    public PreBattleManager getPreBattle() {
        return preBattle;
    }

    public LandsIntegration getLands() {
        return lands;
    }

    private boolean tryInitLands() {
        if (lands != null) {
            return true;
        }
        try {
            lands = LandsIntegration.of(this);
            return lands != null;
        } catch (Throwable t) {
            getLogger().warning("Failed to acquire LandsIntegration: " + t.getMessage());
            return false;
        }
    }
}
