package com.civbound.war.util;

import me.angeschossen.lands.api.land.Land;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LandsDefenderOverride implements Listener {

    private final WarManager warManager;
    private final LandUtil landUtil;
    private final WarConfig config;

    private final Map<UUID, Long> lastMessage = new HashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 3000L;

    public LandsDefenderOverride(WarManager warManager, LandUtil landUtil, WarConfig config) {
        this.warManager = warManager;
        this.landUtil = landUtil;
        this.config = config;
    }

    
    
    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlace(BlockPlaceEvent event) {
        if (BeaconManager.IS_PLUGIN_PLACING) {
            event.setCancelled(false);
            return;
        }

        Player p = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        Battle battle = warManager.battleAt(loc);
        if (battle == null) return;

        UUID uid = p.getUniqueId();
        Side side = battle.sideOf(uid);

        
        if (side == Side.DEFENDER && event.isCancelled() && isNonMemberDefender(uid, battle, loc)) {
            if (wouldEncloseCapPoint(battle, block)) {
                notify(p, "&cYou can't build over/around the capture point.");
                return;
            }
            if (config.isDefenderPlaceAllowAll()) {
                event.setCancelled(false);
            } else if (config.getDefenderPlaceAllow().contains(block.getType())) {
                event.setCancelled(false);
            } else {
                notify(p, "&cYou can't place that block during the battle.");
            }
            return;
        }

        
        if (side == Side.ATTACKER) {
            if (wouldEncloseCapPoint(battle, block)) {
                event.setCancelled(true);
                notify(p, "&cYou can't build over/around the capture point.");
                return;
            }
            if (config.getAttackerPlaceAllow().contains(block.getType())) {
                event.setCancelled(false);
            } else {
                event.setCancelled(true);
                notify(p, "&cYou can only place approved siege blocks here.");
            }
        }
    }

    
    
    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        Battle battle = warManager.battleAt(loc);
        if (battle == null) return;

        UUID uid = p.getUniqueId();
        Side side = battle.sideOf(uid);

        
        if (isBeaconStructureBlock(battle, block)) {
            event.setCancelled(true);
            notify(p, "&cCapture-point blocks cannot be broken.");
            return;
        }

        
        if (side == Side.DEFENDER && event.isCancelled() && isNonMemberDefender(uid, battle, loc)) {
            if (config.isDefenderBreakAllowAll()) {
                event.setCancelled(false);
                return;
            }
            if (isContainer(block) && !config.isDefenderContainerAccess()) {
                notify(p, "&cYou cannot break containers during the battle.");
                return;
            }
            Material m = block.getType();
            if (config.isDefenderBreakDenyList()) {
                if (!config.getDefenderBreakAllow().contains(m)) {
                    event.setCancelled(false);
                } else {
                    notify(p, "&cYou cannot break that block during the battle.");
                }
            } else {
                if (config.getDefenderBreakAllow().contains(m)) {
                    event.setCancelled(false);
                } else {
                    notify(p, "&cYou cannot break that block during the battle.");
                }
            }
            return;
        }

        
        if (side == Side.ATTACKER) {
            if (isContainer(block)) {
                event.setCancelled(true);
                notify(p, "&cYou cannot break containers during the battle.");
                return;
            }
            Material m = block.getType();
            boolean allowed = config.isAttackerBreakDenyList()
                    ? !config.getAttackerBreakAllow().contains(m)
                    : config.getAttackerBreakAllow().contains(m);
            if (allowed) {
                event.setCancelled(false);
            } else {
                event.setCancelled(true);
                notify(p, "&cYou cannot break that block during the battle.");
            }
        }
    }

    
    
    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!event.isCancelled()) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player p = event.getPlayer();
        Location loc = clicked.getLocation();

        Battle battle = warManager.battleAt(loc);
        if (battle == null) return;

        UUID uid = p.getUniqueId();
        Side side = battle.sideOf(uid);

        if (side == Side.DEFENDER && isNonMemberDefender(uid, battle, loc)) {
            if (isContainer(clicked) && !config.isDefenderContainerAccess()) {
                notify(p, "&cYou cannot open containers during the battle.");
                return;
            }
            event.setCancelled(false);
            return;
        }

        if (side == Side.ATTACKER && isContainer(clicked)) {
            notify(p, "&cYou cannot open containers during the battle.");
        }
    }

    
    
    

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        
        
        
        event.blockList().removeIf(block -> {
            Battle battle = warManager.battleAt(block.getLocation());
            if (battle == null) return false;
            if (isBeaconStructureBlock(battle, block)) return true;
            return !config.isAllowExplosions();
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Battle battle = warManager.battleAt(block.getLocation());
            if (battle == null) return false;
            if (isBeaconStructureBlock(battle, block)) return true;
            return !config.isAllowExplosions();
        });
    }

    
    
    

    private boolean isNonMemberDefender(UUID uid, Battle battle, Location loc) {
        Land defenderLand = landUtil.getLandByName(battle.getCampaign().getDefenderLand());
        if (defenderLand == null) return false;

        Land claimAtLoc = landUtil.landAt(loc);
        if (claimAtLoc == null
                || !claimAtLoc.getName().equalsIgnoreCase(defenderLand.getName())) {
            return false;
        }

        Set<UUID> members = landUtil.members(defenderLand);
        return !members.contains(uid);
    }

    private boolean wouldEncloseCapPoint(Battle battle, Block block) {
        int bx = block.getX(), by = block.getY(), bz = block.getZ();
        int radius = (int) config.getCapRadius();
        String world = block.getWorld().getName();
        for (CapState cs : battle.getCaps()) {
            Cap cap = cs.getCap();
            if (!cap.getWorld().equalsIgnoreCase(world)) continue;
            if (cap.inCapCube(bx, by, bz, radius) || cap.inCapShell(bx, by, bz, radius)) return true;
        }
        return false;
    }

    

    private boolean isBeaconStructureBlock(Battle battle, Block block) {
        String world = block.getWorld().getName();
        int bx = block.getX(), by = block.getY(), bz = block.getZ();
        for (CapState cs : battle.getCaps()) {
            Cap cap = cs.getCap();
            if (!cap.getWorld().equalsIgnoreCase(world)) continue;
            int cx = cap.getX(), cy = cap.getY(), cz = cap.getZ();
            
            if (bx == cx && by == cy && bz == cz) return true;
            
            if (bx == cx && by == cy + 1 && bz == cz) return true;
            
            if (by == cy - 1 && Math.abs(bx - cx) <= 1 && Math.abs(bz - cz) <= 1) return true;
        }
        return false;
    }

    private static boolean isContainer(Block block) {
        try {
            return block.getState() instanceof InventoryHolder;
        } catch (Throwable t) {
            return false;
        }
    }

    private void notify(Player p, String msg) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(p.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return;
        lastMessage.put(p.getUniqueId(), now);
        p.sendMessage(config.getPrefix() + WarConfig.color(msg));
    }
}
