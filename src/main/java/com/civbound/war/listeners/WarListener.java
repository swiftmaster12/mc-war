package com.civbound.war.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class WarListener implements Listener {

    private final CivboundWar plugin;
    private final WarManager warManager;
    private final WarConfig config;

    
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 3000L;

    public WarListener(CivboundWar plugin, WarManager warManager, WarConfig config) {
        this.plugin = plugin;
        this.warManager = warManager;
        this.config = config;
    }

    private boolean bypass(Player p) {
        return p.hasPermission("civboundwar.admin");
    }

    private void notify(Player p, String msg) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(p.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) {
            return;
        }
        lastMessage.put(p.getUniqueId(), now);
        p.sendMessage(config.getPrefix() + WarConfig.color(msg));
    }

    

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();

        
        PreBattleManager preBattle = plugin.getPreBattle();
        if (preBattle != null && preBattle.isFrozen(p.getUniqueId())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX()
                    || from.getBlockY() != to.getBlockY()
                    || from.getBlockZ() != to.getBlockZ())) {
                event.setTo(from.clone().setDirection(to.getDirection()));
            }
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)
                && from.getWorld() == to.getWorld()) {
            return;
        }
        if (bypass(p)) {
            return;
        }
        Battle b = warManager.battleAt(to);
        if (b == null) {
            return; 
        }
        if (b.isParticipant(p.getUniqueId())) {
            return;
        }
        
        event.setTo(from);
        notify(p, "&cYou cannot enter an active warzone.");
    }

    

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        
        if (BeaconManager.IS_PLUGIN_PLACING) {
            return;
        }
        Block block = event.getBlock();
        Battle b = warManager.battleAt(block.getLocation());
        if (b == null) {
            return;
        }
        Player p = event.getPlayer();
        if (bypass(p)) {
            return;
        }

        
        
        if (wouldEncloseCapPoint(b, block)) {
            event.setCancelled(true);
            notify(p, "&cYou can't build over/around the capture point.");
            return;
        }

        Side side = b.sideOf(p.getUniqueId());
        if (side == Side.DEFENDER) {
            return; 
        }
        if (side == null) {
            event.setCancelled(true);
            return; 
        }
        
        if (!config.getAttackerPlaceAllow().contains(block.getType())) {
            event.setCancelled(true);
            notify(p, "&cYou can only place approved siege blocks here.");
        }
    }

    

    private boolean wouldEncloseCapPoint(Battle b, Block block) {
        int bx = block.getX();
        int by = block.getY();
        int bz = block.getZ();
        int radius = (int) config.getCapRadius();
        String world = block.getWorld().getName();
        for (CapState cs : b.getCaps()) {
            Cap cap = cs.getCap();
            if (!cap.getWorld().equalsIgnoreCase(world)) {
                continue;
            }
            if (cap.inCapCube(bx, by, bz, radius) || cap.inCapShell(bx, by, bz, radius)) {
                return true;
            }
        }
        return false;
    }

    

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Battle b = warManager.battleAt(block.getLocation());
        if (b == null) {
            return;
        }
        Player p = event.getPlayer();
        
        if (isCapBlock(b, block)) {
            event.setCancelled(true);
            if (!bypass(p)) {
                notify(p, "&cCapture-point blocks cannot be broken.");
            }
            return;
        }
        if (bypass(p)) {
            return;
        }
        Side side = b.sideOf(p.getUniqueId());
        if (side == Side.DEFENDER) {
            return;
        }
        if (side == null) {
            event.setCancelled(true);
            return;
        }
        
        if (isContainer(block)) {
            event.setCancelled(true);
            notify(p, "&cYou cannot break containers during the battle.");
            return;
        }
        Material m = block.getType();
        boolean allowed = config.isAttackerBreakDenyList()
                ? !config.getAttackerBreakAllow().contains(m)
                : config.getAttackerBreakAllow().contains(m);
        if (!allowed) {
            event.setCancelled(true);
            notify(p, "&cYou cannot break that block during the battle.");
        }
    }

    

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !isContainer(block)) {
            return;
        }
        Battle b = warManager.battleAt(block.getLocation());
        if (b == null) {
            return;
        }
        Player p = event.getPlayer();
        if (bypass(p)) {
            return;
        }
        Side side = b.sideOf(p.getUniqueId());
        if (side != Side.DEFENDER) {
            
            event.setCancelled(true);
            notify(p, "&cYou cannot open containers during the battle.");
        }
    }

    

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        
        PreBattleManager preBattle = plugin.getPreBattle();
        if (preBattle != null) {
            UUID damagerId = event.getDamager() instanceof Player pd ? pd.getUniqueId() : null;
            UUID victimId = event.getEntity() instanceof Player pv ? pv.getUniqueId() : null;
            if ((damagerId != null && preBattle.isFrozen(damagerId))
                    || (victimId != null && preBattle.isFrozen(victimId))) {
                event.setCancelled(true);
                return;
            }
        }

        if (!(event.getDamager() instanceof Player p)) {
            return;
        }
        Battle b = warManager.battleForCombatant(p.getUniqueId());
        if (b == null) {
            return;
        }
        if (gearViolation(p)) {
            event.setCancelled(true);
            notify(p, "&cYour gear exceeds the war limit (max " + config.getMaxGearLabel()
                    + "). Remove disallowed armor/weapons.");
        }
    }

    
    private boolean gearViolation(Player p) {
        ItemStack[] armor = p.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (isDisallowedGear(piece)) {
                return true;
            }
        }
        return isDisallowedGear(p.getInventory().getItemInMainHand())
                || isDisallowedGear(p.getInventory().getItemInOffHand());
    }

    private boolean isDisallowedGear(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        Material m = item.getType();
        if (!isGear(m)) {
            return false; 
        }
        if (config.getAllowedGearMaterials().contains(m)) {
            return false;
        }
        return !config.getAllowedExtraArmor().contains(m.name().toLowerCase(Locale.ROOT));
    }

    private static boolean isGear(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || n.endsWith("_SWORD") || n.endsWith("_AXE")
                || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT")
                || n.equals("SHIELD") || n.equals("ELYTRA") || n.equals("MACE");
    }

    

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!config.isBanEnderPearls()) {
            return;
        }
        Projectile proj = event.getEntity();
        if (!(proj instanceof EnderPearl)) {
            return;
        }
        ProjectileSource shooter = proj.getShooter();
        if (!(shooter instanceof Player p)) {
            return;
        }
        if (warManager.battleForCombatant(p.getUniqueId()) != null) {
            event.setCancelled(true);
            notify(p, "&cEnder pearls are banned during the battle.");
        }
    }

    

    private boolean isCapBlock(Battle b, Block block) {
        String world = block.getWorld().getName();
        for (CapState cs : b.getCaps()) {
            Cap cap = cs.getCap();
            if (cap.getX() == block.getX() && cap.getY() == block.getY()
                    && cap.getZ() == block.getZ() && cap.getWorld().equalsIgnoreCase(world)) {
                return true;
            }
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
}
