package com.civbound.war;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FeedbackManager {

    private final CivboundWar plugin;
    private final WarConfig config;
    private final Server server;

    
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public FeedbackManager(CivboundWar plugin, WarConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.server = plugin.getServer();
    }

    public void startBattle(Battle battle) {
        if (!config.isBossBarEnabled()) {
            return;
        }
        for (Player p : server.getOnlinePlayers()) {
            if (battle.isParticipant(p.getUniqueId())) {
                ensureBar(p);
            }
        }
    }

    public void endBattle(Battle battle) {
        for (UUID id : new ArrayList<>(bars.keySet())) {
            if (battle.isParticipant(id)) {
                BossBar bar = bars.remove(id);
                if (bar != null) {
                    bar.removeAll();
                }
            }
        }
    }

    
    public void clear() {
        for (BossBar bar : bars.values()) {
            bar.removeAll();
        }
        bars.clear();
    }

    private BossBar ensureBar(Player p) {
        return bars.computeIfAbsent(p.getUniqueId(), k -> {
            BossBar bar = server.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
            bar.addPlayer(p);
            return bar;
        });
    }

    
    public void tick(Battle battle) {
        long now = System.currentTimeMillis();
        if (config.isBossBarEnabled()) {
            updateBars(battle, now);
        }
        if (config.isParticlesEnabled()) {
            emitParticles(battle);
        }
    }

    private void updateBars(Battle battle, long now) {
        boolean prep = battle.isInPrep(now);
        long secsLeft = battle.millisRemaining(now) / 1000L;
        for (Player p : server.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (!battle.isParticipant(id)) {
                
                BossBar stale = bars.remove(id);
                if (stale != null) {
                    stale.removeAll();
                }
                continue;
            }
            BossBar bar = ensureBar(p);
            CapState near = nearestCap(battle, p);
            if (prep) {
                bar.setColor(BarColor.YELLOW);
                bar.setProgress(1.0);
                bar.setTitle(WarConfig.color("&ePrep phase &7- battle " + battle.getNumber()
                        + " begins soon. Score " + fmt(battle.score(Side.ATTACKER)) + " - "
                        + fmt(battle.score(Side.DEFENDER))));
                continue;
            }
            if (near == null) {
                bar.setColor(BarColor.WHITE);
                bar.setProgress(clamp((double) secsLeft / Math.max(1L, config.getBattleDurationSeconds())));
                bar.setTitle(WarConfig.color("&fATK &c" + fmt(battle.score(Side.ATTACKER))
                        + " &7| &fDEF &a" + fmt(battle.score(Side.DEFENDER))
                        + " &7| " + timeStr(secsLeft)));
                continue;
            }
            updateCapBar(bar, battle, near, p, secsLeft);
        }
    }

    private void updateCapBar(BossBar bar, Battle battle, CapState near, Player p, long secsLeft) {
        Cap cap = near.getCap();
        double frac = near.progressFraction(config);
        Side holder = near.getHolder();
        String holderStr = holder == null ? "&7neutral"
                : (holder == Side.ATTACKER ? "&cAttackers" : "&aDefenders");

        BarColor color;
        if (near.isContested()) {
            color = BarColor.PURPLE;
        } else if (holder == Side.ATTACKER) {
            color = BarColor.RED;
        } else if (holder == Side.DEFENDER) {
            color = BarColor.GREEN;
        } else {
            color = BarColor.WHITE;
        }
        bar.setColor(color);
        bar.setProgress(near.getHolder() != null && near.getPushing() == null ? 1.0 : clamp(frac));

        long[] counts = headCounts(battle, near, p);
        bar.setTitle(WarConfig.color("&f" + cap.getId() + " &7[" + cap.getTier() + "] "
                + holderStr + " &7| &c" + counts[0] + " &7v &a" + counts[1]
                + (near.isContested() ? " &dCONTESTED" : "")
                + " &7| " + timeStr(secsLeft)));
    }

    
    private long[] headCounts(Battle battle, CapState cs, Player viewer) {
        Location center = cs.getCap().location(server);
        long atk = 0;
        long def = 0;
        if (center == null) {
            return new long[] {0, 0};
        }
        double radiusSq = config.getCapRadius() * config.getCapRadius();
        for (Player p : server.getOnlinePlayers()) {
            if (p.getWorld() != center.getWorld()) {
                continue;
            }
            if (p.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            Side side = battle.sideOf(p.getUniqueId());
            if (side == Side.ATTACKER) {
                atk++;
            } else if (side == Side.DEFENDER) {
                def++;
            }
        }
        return new long[] {atk, def};
    }

    private CapState nearestCap(Battle battle, Player p) {
        CapState best = null;
        double bestSq = Double.MAX_VALUE;
        for (CapState cs : battle.getCaps()) {
            Location loc = cs.getCap().location(server);
            if (loc == null || loc.getWorld() != p.getWorld()) {
                continue;
            }
            double d = p.getLocation().distanceSquared(loc);
            if (d < bestSq) {
                bestSq = d;
                best = cs;
            }
        }
        
        double surface = config.getCapRadius() * 3.0;
        if (best != null && bestSq <= surface * surface) {
            return best;
        }
        return null;
    }

    private void emitParticles(Battle battle) {
        for (CapState cs : battle.getCaps()) {
            Location loc = cs.getCap().location(server);
            if (loc == null) {
                continue;
            }
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            try {
                if (cs.isContested()) {
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 12, 0.4, 0.6, 0.4,
                            new Particle.DustOptions(Color.PURPLE, 1.6f));
                } else if (cs.getHolder() == null || cs.getHolder() == Side.DEFENDER) {
                    
                    Color c = cs.getHolder() == null ? Color.WHITE : Color.LIME;
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 8, 0.3, 0.5, 0.3,
                            new Particle.DustOptions(c, 1.3f));
                } else {
                    world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 6, 0.3, 0.5, 0.3,
                            new Particle.DustOptions(Color.RED, 1.3f));
                }
            } catch (Throwable ignored) {
                
            }
        }
    }

    

    public List<String> statusLines(Battle battle) {
        long now = System.currentTimeMillis();
        List<String> out = new ArrayList<>();
        Campaign c = battle.getCampaign();
        out.add(WarConfig.color("&6War: &e" + c.getAttackerLand() + " &7vs &e" + c.getDefenderLand()));
        out.add(WarConfig.color("&7Series (siege points): &c" + c.siegePoints(Side.ATTACKER)
                + " &7- &a" + c.siegePoints(Side.DEFENDER)
                + " &7(first to " + c.siegePointsToWin(config) + ")"));
        out.add(WarConfig.color("&7Battle &f" + battle.getNumber() + "&7: &cATK " + fmt(battle.score(Side.ATTACKER))
                + " &7- &aDEF " + fmt(battle.score(Side.DEFENDER))
                + " &7| " + (battle.isInPrep(now) ? "&eprep" : timeStr(battle.millisRemaining(now) / 1000L))));
        int r = (int) config.getCapRadius();
        int side = 2 * r + 1;
        out.add(WarConfig.color("&7Cap zone: &f" + side + "×" + side + "×" + side
                + " &7cube (radius &f" + r + "&7 blocks each axis)"));
        for (CapState cs : battle.getCaps()) {
            Side h = cs.getHolder();
            String hs = h == null ? "&7neutral" : (h == Side.ATTACKER ? "&cATK" : "&aDEF");
            out.add(WarConfig.color("  &f" + cs.getCap().getId() + " &7[" + cs.getCap().getTier() + "] "
                    + hs + (cs.isContested() ? " &dcontested" : "")
                    + " &7" + Math.round(cs.progressFraction(config) * 100) + "%"));
        }
        return out;
    }

    

    public void previewCubeEdges(String defenderLand, CapStore capStore) {
        if (!config.isParticlesEnabled()) return;
        int r = (int) config.getCapRadius();
        for (Cap cap : capStore.getCaps(defenderLand)) {
            Location center = cap.location(plugin.getServer());
            if (center == null) continue;
            World world = center.getWorld();
            if (world == null) continue;
            spawnCubeEdges(world, cap.getX(), cap.getY(), cap.getZ(), r);
        }
    }

    
    private void spawnCubeEdges(World world, int cx, int cy, int cz, int r) {
        
        
        int step = Math.max(1, r / 4);
        for (int d = -r; d <= r; d += step) {
            
            emit(world, cx + d, cy - r, cz - r);
            emit(world, cx + d, cy - r, cz + r);
            emit(world, cx + d, cy + r, cz - r);
            emit(world, cx + d, cy + r, cz + r);
            
            emit(world, cx - r, cy + d, cz - r);
            emit(world, cx - r, cy + d, cz + r);
            emit(world, cx + r, cy + d, cz - r);
            emit(world, cx + r, cy + d, cz + r);
            
            emit(world, cx - r, cy - r, cz + d);
            emit(world, cx - r, cy + r, cz + d);
            emit(world, cx + r, cy - r, cz + d);
            emit(world, cx + r, cy + r, cz + d);
        }
    }

    private void emit(World world, int x, int y, int z) {
        try {
            world.spawnParticle(Particle.DUST,
                    new Location(world, x + 0.5, y + 0.5, z + 0.5),
                    3, 0.1, 0.1, 0.1,
                    new Particle.DustOptions(Color.WHITE, 1.2f));
        } catch (Throwable ignored) {
        }
    }

    
    public List<String> mapGrid(Battle battle) {
        List<String> out = new ArrayList<>();
        List<CapState> caps = battle.getCaps();
        if (caps.isEmpty()) {
            out.add(WarConfig.color("&7(no caps)"));
            return out;
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (CapState cs : caps) {
            minX = Math.min(minX, cs.getCap().getX());
            maxX = Math.max(maxX, cs.getCap().getX());
            minZ = Math.min(minZ, cs.getCap().getZ());
            maxZ = Math.max(maxZ, cs.getCap().getZ());
        }
        int cols = 9;
        int rows = 9;
        char[][] grid = new char[rows][cols];
        for (char[] row : grid) {
            java.util.Arrays.fill(row, '.');
        }
        int spanX = Math.max(1, maxX - minX);
        int spanZ = Math.max(1, maxZ - minZ);
        for (CapState cs : caps) {
            int gx = (int) Math.round((double) (cs.getCap().getX() - minX) / spanX * (cols - 1));
            int gz = (int) Math.round((double) (cs.getCap().getZ() - minZ) / spanZ * (rows - 1));
            Side h = cs.getHolder();
            char ch = h == null ? 'o' : (h == Side.ATTACKER ? 'A' : 'D');
            grid[gz][gx] = ch;
        }
        out.add(WarConfig.color("&6War map &7(A=attacker, D=defender, o=neutral):"));
        for (char[] row : grid) {
            StringBuilder sb = new StringBuilder("&7");
            for (char ch : row) {
                sb.append(switch (ch) {
                    case 'A' -> "&cA";
                    case 'D' -> "&aD";
                    case 'o' -> "&fo";
                    default -> "&8.";
                }).append(' ');
            }
            out.add(WarConfig.color(sb.toString()));
        }
        return out;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String fmt(double d) {
        return String.valueOf(Math.round(d));
    }

    private static String timeStr(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
