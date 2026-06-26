package com.civbound.war;

import me.angeschossen.lands.api.land.Land;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class WarCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "civboundwar.admin";
    private static final String STATUS = "civboundwar.status";

    private final CivboundWar plugin;

    public WarCommand(CivboundWar plugin) {
        this.plugin = plugin;
    }

    private WarManager war() {
        return plugin.getWarManager();
    }

    private WarConfig config() {
        return plugin.getWarConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> handleCreate(sender, args);
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender, args);
            case "status" -> handleStatus(sender, args);
            case "map" -> handleMap(sender, args);
            case "preview" -> handlePreview(sender, args);
            case "cap" -> handleCap(sender, args);
            case "ally" -> handleAlly(sender, args);
            case "staging" -> handleStaging(sender, args);
            case "vassal" -> handleVassal(sender, args);
            case "spawn" -> handleSpawn(sender, args);
            case "reload" -> handleReload(sender);
            default -> help(sender);
        }
        return true;
    }

    

    private void handleCreate(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        if (args.length < 4) {
            msg(sender, "&7Usage: /war create <name> <attacker> <defender>");
            return;
        }
        WarManager.StartOutcome outcome = war().createNamed(args[1], args[2], args[3]);
        switch (outcome.result()) {
            case SAME_LAND -> msg(sender, "&cAttacker and defender must be different lands.");
            case NAME_TAKEN -> msg(sender, "&cA war named &f" + args[1] + " &c(or between those lands) already exists.");
            case NO_ATTACKER -> msg(sender, "&cAttacker land not found: &f" + args[2]);
            case NO_DEFENDER -> msg(sender, "&cDefender land not found: &f" + args[3]);
            case LAND_BUSY -> msg(sender, "&cOne of those lands is already in an active battle.");
            case CAMPAIGN_CREATED -> msg(sender, "&aWar &f" + outcome.campaign().getName() + " &acreated: &f"
                    + outcome.campaign().getAttackerLand() + " &7vs &f" + outcome.campaign().getDefenderLand()
                    + "&a. Register allies, set staging, then run &f/war start " + outcome.campaign().getName() + "&a.");
            default -> msg(sender, "&cCould not create that war.");
        }
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        
        if (args.length == 2) {
            WarManager.StartOutcome named = war().startNamed(args[1]);
            if (named.result() == WarManager.StartResult.NO_WAR) {
                msg(sender, "&cNo war named &f" + args[1] + "&c. Use /war start <attacker> <defender> or /war create.");
                return;
            }
            reportStart(sender, named);
            return;
        }
        if (args.length < 3) {
            msg(sender, "&7Usage: /war start <name>  &7or  &7/war start <attacker> <defender>");
            return;
        }
        WarManager.StartOutcome outcome = war().start(args[1], args[2]);
        reportStart(sender, outcome);
    }

    private void reportStart(CommandSender sender, WarManager.StartOutcome outcome) {
        switch (outcome.result()) {
            case SAME_LAND -> msg(sender, "&cAttacker and defender must be different lands.");
            case NO_ATTACKER -> msg(sender, "&cAttacker land not found.");
            case NO_DEFENDER -> msg(sender, "&cDefender land not found.");
            case LAND_BUSY -> msg(sender, "&cOne of those lands is already in an active battle.");
            case NO_CAPS -> msg(sender, "&cThe defender has no capture points. Add some with /war cap add.");
            case SERIES_FINISHED -> msg(sender, "&eThat war is already decided.");
            case BATTLE_ALREADY_ACTIVE -> msg(sender, "&eA battle in that war is already running.");
            case CAMPAIGN_CREATED -> msg(sender, "&aWar created: &f" + outcome.campaign().getAttackerLand()
                    + " &7vs &f" + outcome.campaign().getDefenderLand()
                    + "&a. Register allies with /war ally, then run /war start again to begin battle 1.");
            case BATTLE_STARTED -> msg(sender, "&aBattle &f" + outcome.battle().getNumber() + "&a started.");
        }
    }

    private void handleStop(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        Campaign c = resolveCampaign(args, 1);
        if (c == null) {
            msg(sender, "&7Usage: /war stop <attacker> <defender>  (or /war stop <land>)");
            return;
        }
        war().forceStop(c);
        msg(sender, "&aWar stopped.");
    }

    

    private void handleStatus(CommandSender sender, String[] args) {
        if (!require(sender, STATUS)) {
            return;
        }
        Battle b = resolveBattle(sender, args, 1);
        if (b == null) {
            return;
        }
        for (String line : plugin.getFeedback().statusLines(b)) {
            sender.sendMessage(line);
        }
    }

    private void handleMap(CommandSender sender, String[] args) {
        if (!require(sender, STATUS)) {
            return;
        }
        Battle b = resolveBattle(sender, args, 1);
        if (b == null) {
            return;
        }
        for (String line : plugin.getFeedback().mapGrid(b)) {
            sender.sendMessage(line);
        }
    }

    

    private void handlePreview(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&7Usage: /war preview <name|land>  &7(toggles beacon previews at that war's caps)");
            return;
        }
        Campaign c = war().resolve(args[1]);
        if (c == null) {
            msg(sender, "&cNo such war: &f" + args[1]);
            return;
        }
        int result = plugin.getBeaconManager().togglePreview(c.getName(), c.getDefenderLand());
        int r = (int) config().getCapRadius();
        int side = 2 * r + 1;
        switch (result) {
            case -1 -> msg(sender, "&aPreview beacons for &f" + c.getName() + " &acleared.");
            case -2 -> msg(sender, "&cThat war's defender has no capture points. Add some with /war cap add.");
            default -> {
                msg(sender, "&aShowing &f" + result + " &apreview beacon(s) for &f" + c.getName()
                        + " &7(" + config().getBeaconPreviewSeconds() + "s; run again to clear).");
                msg(sender, "&7Cap zone: &f" + side + "×" + side + "×" + side
                        + " &7cube (radius &f" + r + "&7 blocks each axis).");
                
                plugin.getFeedback().previewCubeEdges(c.getDefenderLand(), plugin.getCapStore());
            }
        }
    }

    

    private void handleCap(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&7Usage: /war cap <add|remove|list> ...");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "add" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "&cYou must be in-game to add a cap at your location.");
                    return;
                }
                if (args.length < 4) {
                    msg(sender, "&7Usage: /war cap add <land> <core|mid|exterior>");
                    return;
                }
                String land = args[2];
                String tier = args[3].toLowerCase(Locale.ROOT);
                if (!config().isValidTier(tier)) {
                    msg(sender, "&cInvalid tier. Use core, mid or exterior.");
                    return;
                }
                if (war().getLandUtil().getLandByName(land) == null) {
                    msg(sender, "&cLand not found: &f" + land);
                    return;
                }
                var loc = p.getLocation();
                Cap cap = plugin.getCapStore().add(land, loc.getWorld().getName(),
                        loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tier);
                msg(sender, "&aAdded cap &f" + cap.getId() + " &ato &f" + land + " &7at your position.");
            }
            case "remove" -> {
                if (args.length < 4) {
                    msg(sender, "&7Usage: /war cap remove <land> <id>");
                    return;
                }
                boolean removed = plugin.getCapStore().remove(args[2], args[3]);
                msg(sender, removed ? "&aRemoved cap &f" + args[3] : "&cNo such cap: &f" + args[3]);
            }
            case "list" -> {
                if (args.length < 3) {
                    msg(sender, "&7Usage: /war cap list <land>");
                    return;
                }
                List<Cap> caps = plugin.getCapStore().getCaps(args[2]);
                if (caps.isEmpty()) {
                    msg(sender, "&7No caps registered for &f" + args[2]);
                    return;
                }
                msg(sender, "&6Caps for &f" + args[2] + "&6:");
                for (Cap c : caps) {
                    sender.sendMessage(WarConfig.color("  &f" + c));
                }
            }
            default -> msg(sender, "&7Usage: /war cap <add|remove|list> ...");
        }
    }

    

    private void handleAlly(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&7Usage: /war ally <add|remove|list> <attacker> <defender> [side] [allyLand]");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        if (op.equals("list")) {
            if (args.length < 4) {
                msg(sender, "&7Usage: /war ally list <attacker> <defender>");
                return;
            }
            Campaign c = war().getCampaign(args[2], args[3]);
            if (c == null) {
                msg(sender, "&cNo such war.");
                return;
            }
            msg(sender, "&6Attacker allies: &f" + String.join(", ", c.allies(Side.ATTACKER)));
            msg(sender, "&6Defender allies: &f" + String.join(", ", c.allies(Side.DEFENDER)));
            return;
        }
        if (args.length < 6) {
            msg(sender, "&7Usage: /war ally " + op + " <attacker> <defender> <attacker|defender> <allyLand>");
            return;
        }
        Campaign c = war().getCampaign(args[2], args[3]);
        if (c == null) {
            msg(sender, "&cNo such war. Create it first with /war start.");
            return;
        }
        Side side = parseSide(args[4]);
        if (side == null) {
            msg(sender, "&cSide must be 'attacker' or 'defender'.");
            return;
        }
        String allyLand = args[5];
        Land ally = war().getLandUtil().getLandByName(allyLand);
        if (ally == null) {
            msg(sender, "&cAlly land not found: &f" + allyLand);
            return;
        }
        List<String> list = c.allies(side);
        if (op.equals("add")) {
            Land warring = war().getLandUtil().getLandByName(c.warringLand(side));
            if (!war().getLandUtil().isFormalAlly(warring, ally)) {
                msg(sender, "&c" + allyLand + " is not a formal Lands ally of " + c.warringLand(side) + ".");
                return;
            }
            if (list.size() >= config().getMaxAlliesPerSide()) {
                msg(sender, "&cThat side already has the maximum of " + config().getMaxAlliesPerSide() + " allies.");
                return;
            }
            if (list.stream().anyMatch(allyLand::equalsIgnoreCase)) {
                msg(sender, "&eThat ally is already registered.");
                return;
            }
            list.add(ally.getName());
            msg(sender, "&aRegistered &f" + ally.getName() + " &aas a " + side.name().toLowerCase(Locale.ROOT) + " ally.");
        } else if (op.equals("remove")) {
            boolean removed = list.removeIf(allyLand::equalsIgnoreCase);
            msg(sender, removed ? "&aRemoved ally &f" + allyLand : "&cThat ally was not registered.");
        } else {
            msg(sender, "&7Usage: /war ally <add|remove|list> ...");
        }
    }

    

    private void handleStaging(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&7Usage: /war staging <set|clear> ...");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "set" -> {
                
                
                Campaign c;
                String stagingLand;
                if (args.length == 4) {
                    c = war().resolve(args[2]);
                    stagingLand = args[3];
                } else if (args.length >= 5) {
                    c = war().getCampaign(args[2], args[3]);
                    stagingLand = args[4];
                } else {
                    msg(sender, "&7Usage: /war staging set <name> <stagingLand>  &7or  &7<attacker> <defender> <stagingLand>");
                    return;
                }
                if (c == null) {
                    msg(sender, "&cNo such war. Create it first with /war create or /war start.");
                    return;
                }
                StagingManager.Outcome outcome = plugin.getStagingManager().set(c, stagingLand);
                switch (outcome.result()) {
                    case NO_CAMPAIGN -> msg(sender, "&cNo such war.");
                    case NO_STAGING_LAND -> msg(sender, "&cStaging land not found: &f" + stagingLand);
                    case STAGING_IS_DEFENDER -> msg(sender, "&cThe staging base can't be the defender's claim.");
                    case TOO_CLOSE -> msg(sender, "&cStaging land is too close to the defender. It must be at "
                            + "least &f" + outcome.minDistanceChunks() + " &cchunks away.");
                    case OK -> msg(sender, "&aStaging base &f" + stagingLand + " &alinked. Trusted &f"
                            + outcome.trustedCount() + " &aeligible attacking-side members in.");
                    default -> msg(sender, "&cCould not set staging base.");
                }
            }
            case "clear" -> {
                if (args.length < 3) {
                    msg(sender, "&7Usage: /war staging clear <attacker>");
                    return;
                }
                StagingManager.Result r = plugin.getStagingManager().clear(args[2]);
                msg(sender, r == StagingManager.Result.OK
                        ? "&aCleared the staging base for &f" + args[2] + " &7(untrusted everyone the plugin added)."
                        : "&cNo staging base linked for &f" + args[2]);
            }
            default -> msg(sender, "&7Usage: /war staging <set|clear> ...");
        }
    }

    

    private void handleVassal(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) {
            return;
        }
        if (args.length < 2) {
            msg(sender, "&7Usage: /war vassal <set|clear|list> ...");
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        switch (op) {
            case "set" -> {
                if (args.length < 4) {
                    msg(sender, "&7Usage: /war vassal set <vassalLand> <overlordLand>");
                    return;
                }
                if (war().getLandUtil().getLandByName(args[2]) == null) {
                    msg(sender, "&cVassal land not found: &f" + args[2]);
                    return;
                }
                if (war().getLandUtil().getLandByName(args[3]) == null) {
                    msg(sender, "&cOverlord land not found: &f" + args[3]);
                    return;
                }
                plugin.getVassalManager().set(args[2], args[3]);
                msg(sender, "&aSet &f" + args[2] + " &aas a vassal of &f" + args[3]
                        + " &7for " + config().getVassalDurationDays() + " days.");
            }
            case "clear" -> {
                if (args.length < 3) {
                    msg(sender, "&7Usage: /war vassal clear <vassalLand>");
                    return;
                }
                boolean cleared = plugin.getVassalManager().clear(args[2]);
                msg(sender, cleared ? "&aCleared vassalage of &f" + args[2] : "&cThat land is not a vassal.");
            }
            case "list" -> {
                var list = plugin.getVassalManager().list();
                if (list.isEmpty()) {
                    msg(sender, "&7No active vassal relationships.");
                    return;
                }
                msg(sender, "&6Vassals:");
                long now = System.currentTimeMillis();
                for (VassalManager.Vassalship v : list) {
                    long days = Math.max(0, (v.getEndMillis() - now) / 86_400_000L);
                    sender.sendMessage(WarConfig.color("  &f" + v.getVassal() + " &7-> &f" + v.getOverlord()
                            + " &7(" + days + "d left)"));
                }
            }
            default -> msg(sender, "&7Usage: /war vassal <set|clear|list> ...");
        }
    }

    
    
    private void handleSpawn(CommandSender sender, String[] args) {
        if (!require(sender, ADMIN)) return;
        if (!(sender instanceof org.bukkit.entity.Player p)) {
            msg(sender, "&cMust be a player to set a spawn point.");
            return;
        }
        if (args.length < 2) {
            msg(sender, "&7Usage: /war spawn set <attacker|defender> <attackerLand> <defenderLand>");
            msg(sender, "&7       /war spawn clear <attackerLand> <defenderLand>");
            return;
        }
        SpawnStore spawnStore = plugin.getSpawnStore();
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (args.length < 5) {
                    msg(sender, "&7Usage: /war spawn set <attacker|defender> <attackerLand> <defenderLand>");
                    return;
                }
                Side side = args[2].equalsIgnoreCase("attacker") ? Side.ATTACKER
                        : args[2].equalsIgnoreCase("defender") ? Side.DEFENDER : null;
                if (side == null) {
                    msg(sender, "&cSide must be 'attacker' or 'defender'.");
                    return;
                }
                spawnStore.setSpawn(args[3], args[4], side, p.getLocation());
                msg(sender, "&aSpawn point set for &f" + args[2] + " &ain war &f" + args[3] + " vs " + args[4] + "&a.");
            }
            case "clear" -> {
                if (args.length < 4) {
                    msg(sender, "&7Usage: /war spawn clear <attackerLand> <defenderLand>");
                    return;
                }
                spawnStore.clearSpawns(args[2], args[3]);
                msg(sender, "&aSpawn points cleared for &f" + args[2] + " vs " + args[3] + "&a.");
            }
            default -> msg(sender, "&7Usage: /war spawn set <attacker|defender> <attackerLand> <defenderLand>");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!require(sender, ADMIN)) {
            return;
        }
        plugin.reloadWarConfig();
        msg(sender, "&aCivboundWar config reloaded.");
    }

    

    private Battle resolveBattle(CommandSender sender, String[] args, int idx) {
        if (args.length > idx) {
            Campaign c = war().resolve(args[idx]);
            if (c == null || c.getCurrentBattle() == null || !c.getCurrentBattle().isActive()) {
                msg(sender, "&cNo active battle for &f" + args[idx]);
                return null;
            }
            return c.getCurrentBattle();
        }
        List<Battle> active = war().activeBattles();
        if (active.isEmpty()) {
            msg(sender, "&7There are no active battles.");
            return null;
        }
        if (active.size() > 1) {
            msg(sender, "&7Multiple battles active; specify a land: /war status <land>");
            return null;
        }
        return active.get(0);
    }

    private Campaign resolveCampaign(String[] args, int idx) {
        if (args.length > idx + 1) {
            Campaign c = war().getCampaign(args[idx], args[idx + 1]);
            if (c != null) {
                return c;
            }
        }
        if (args.length > idx) {
            return war().resolve(args[idx]);
        }
        return null;
    }

    private static Side parseSide(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        if (t.startsWith("a")) {
            return Side.ATTACKER;
        }
        if (t.startsWith("d")) {
            return Side.DEFENDER;
        }
        return null;
    }

    private boolean require(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) {
            return true;
        }
        msg(sender, "&cYou don't have permission to do that.");
        return false;
    }

    private void msg(CommandSender sender, String msg) {
        sender.sendMessage(config().getPrefix() + WarConfig.color(msg));
    }

    private void help(CommandSender sender) {
        msg(sender, "&6CivboundWar commands:");
        sender.sendMessage(WarConfig.color("  &f/war create <name> <attacker> <defender>"));
        sender.sendMessage(WarConfig.color("  &f/war start <name> &7| &f/war start <attacker> <defender>"));
        sender.sendMessage(WarConfig.color("  &f/war stop <name|land>"));
        sender.sendMessage(WarConfig.color("  &f/war status [name|land] &7| &f/war map [name|land]"));
        sender.sendMessage(WarConfig.color("  &f/war preview <name|land>"));
        sender.sendMessage(WarConfig.color("  &f/war cap <add|remove|list> ..."));
        sender.sendMessage(WarConfig.color("  &f/war ally <add|remove|list> ..."));
        sender.sendMessage(WarConfig.color("  &f/war staging <set|clear> ..."));
        sender.sendMessage(WarConfig.color("  &f/war vassal <set|clear|list> ..."));
        sender.sendMessage(WarConfig.color("  &f/war reload"));
    }

    

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], Arrays.asList("create", "start", "stop", "status", "map", "preview",
                    "cap", "ally", "staging", "vassal", "reload"));
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "cap", "ally" -> {
                    return filter(args[1], Arrays.asList("add", "remove", "list"));
                }
                case "vassal" -> {
                    return filter(args[1], Arrays.asList("set", "clear", "list"));
                }
                case "staging" -> {
                    return filter(args[1], Arrays.asList("set", "clear"));
                }
                case "start", "stop", "status", "map", "preview" -> {
                    List<String> opts = warNames();
                    opts.addAll(landNames());
                    return filter(args[1], opts);
                }
                case "create" -> {
                    return List.of();
                }
                default -> {
                    return List.of();
                }
            }
        }
        if (args.length == 3) {
            if (sub.equals("start") || sub.equals("stop") || sub.equals("create")) {
                return filter(args[2], landNames());
            }
            if (sub.equals("cap") || sub.equals("ally") || sub.equals("vassal") || sub.equals("staging")) {
                return filter(args[2], landNames());
            }
        }
        if (args.length == 4) {
            if (sub.equals("create")) {
                return filter(args[3], landNames());
            }
            if (sub.equals("cap") && args[1].equalsIgnoreCase("add")) {
                return filter(args[3], Arrays.asList("core", "mid", "exterior"));
            }
            if (sub.equals("ally")) {
                return filter(args[3], landNames());
            }
            if (sub.equals("vassal") && args[1].equalsIgnoreCase("set")) {
                return filter(args[3], landNames());
            }
            if (sub.equals("staging") && args[1].equalsIgnoreCase("set")) {
                return filter(args[3], landNames());
            }
        }
        if (args.length == 5 && sub.equals("staging") && args[1].equalsIgnoreCase("set")) {
            return filter(args[4], landNames());
        }
        if (args.length == 5 && sub.equals("ally")) {
            return filter(args[4], Arrays.asList("attacker", "defender"));
        }
        if (args.length == 6 && sub.equals("ally")) {
            return filter(args[5], landNames());
        }
        return List.of();
    }

    private List<String> warNames() {
        List<String> names = new ArrayList<>();
        for (Campaign c : war().getCampaigns()) {
            if (c.getName() != null) {
                names.add(c.getName());
            }
        }
        return names;
    }

    private List<String> landNames() {
        List<String> names = new ArrayList<>();
        for (Land l : war().getLandUtil().allLands()) {
            if (l != null && l.getName() != null) {
                names.add(l.getName());
            }
        }
        return names;
    }

    private static List<String> filter(String prefix, List<String> options) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
