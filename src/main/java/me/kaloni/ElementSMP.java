package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.StringUtil;
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility1 = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility2 = new HashMap<>();
    public static HashMap<UUID, Long> cdDoubleJump = new HashMap<>();
    public static HashMap<UUID, Long> silencedPlayers = new HashMap<>();
    private final String[] elementList = {"Wind", "Fire", "Water", "Earth", "Lightning", "Void"};

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        
        // Reroll Command
        RerollCommand rerollCmd = new RerollCommand();
        getCommand("give-reroll").setExecutor(rerollCmd);
        getCommand("give-reroll").setTabCompleter(rerollCmd);
        
        // Admin Element Command
        AdminElementHandler adminHandler = new AdminElementHandler(this);
        getCommand("elementsmp").setExecutor(adminHandler);
        getCommand("elementsmp").setTabCompleter(adminHandler);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) updateActionBarHUD(p);
        }, 0L, 20L);
    }

    public void setPlayerElement(Player p, String element) {
        playerElements.put(p.getUniqueId(), element);
        p.setAllowFlight(element.equalsIgnoreCase("Wind"));
    }

    public void startRerollAnimation(Player p) {
        new BukkitRunnable() {
            int ticks = 0;
            final Random rand = new Random();
            @Override
            public void run() {
                if (ticks >= 100) { // 20 Seconds
                    String finalEl = elementList[rand.nextInt(elementList.length)];
                    setPlayerElement(p, finalEl);
                    p.sendTitle(getElementColor(finalEl) + "§l" + finalEl.toUpperCase(), "§7Power Awakened!", 10, 70, 20);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    this.cancel();
                    return;
                }
                String randomEl = elementList[rand.nextInt(elementList.length)];
                p.sendTitle("§k||| " + getElementColor(randomEl) + randomEl.toUpperCase() + " §r§k|||", "§fSelecting...", 0, 10, 0);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
                ticks++;
            }
        }.runTaskTimer(this, 0L, 4L); 
    }

    private void updateActionBarHUD(Player p) {
        long now = System.currentTimeMillis();
        if (silencedPlayers.getOrDefault(p.getUniqueId(), 0L) > now) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§d§lVOID SILENCE: " + ((silencedPlayers.get(p.getUniqueId()) - now) / 1000) + "s"));
            return;
        }
        long cd1 = cdAbility1.getOrDefault(p.getUniqueId(), 0L) - now;
        long cd2 = cdAbility2.getOrDefault(p.getUniqueId(), 0L) - now;
        if (cd1 > 0 || cd2 > 0) {
            String msg = (cd1 > 0 ? "§6A1: " + (cd1 / 1000) + "s " : "") + (cd2 > 0 ? "§eA2: " + (cd2 / 1000) + "s" : "");
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
        } else {
            String el = playerElements.getOrDefault(p.getUniqueId(), "Wind");
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§7Element: " + getElementColor(el) + "§l" + el.toUpperCase()));
        }
    }

    public String getElementColor(String el) {
        return switch (el.toLowerCase()) {
            case "fire" -> "§c"; case "water" -> "§3"; case "earth" -> "§2";
            case "lightning" -> "§e"; case "void" -> "§5"; default -> "§b";
        };
    }

    @EventHandler public void onRerollUse(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Reroll")) {
            e.setCancelled(true); item.setAmount(item.getAmount() - 1); startRerollAnimation(e.getPlayer());
        }
    }

    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        if (silencedPlayers.getOrDefault(id, 0L) > System.currentTimeMillis()) return;
        String e = playerElements.getOrDefault(id, "Wind");
        HashMap<UUID, Long> cdMap = (num == 1) ? cdAbility1 : cdAbility2;
        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) return;
        
        if (num == 1) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§lUSED: " + e.toUpperCase() + " A1"));
            // (Ability 1 Logic - Tornado/Launch/Fire etc)
            cdMap.put(id, System.currentTimeMillis() + 40000);
        } else {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§lUSED: " + e.toUpperCase() + " A2"));
            // (Ability 2 Logic - Dash/Silence etc)
            cdMap.put(id, System.currentTimeMillis() + (e.equalsIgnoreCase("void") ? 120000 : 60000));
        }
    }

    @EventHandler public void onFKey(PlayerSwapHandItemsEvent event) { if (useHotkeys.getOrDefault(event.getPlayer().getUniqueId(), false)) { event.setCancelled(true); triggerAbility(event.getPlayer(), event.getPlayer().isSneaking() ? 2 : 1); } }
    @EventHandler public void onJoin(PlayerJoinEvent event) { playerElements.putIfAbsent(event.getPlayer().getUniqueId(), "Wind"); if (playerElements.get(event.getPlayer().getUniqueId()).equals("Wind")) event.getPlayer().setAllowFlight(true); }
    public String[] getElementList() { return elementList; }
}

// --- COMMAND CLASSES ---

class RerollCommand implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!s.isOp()) return true;
        Player target = (args.length > 0) ? Bukkit.getPlayer(args[0]) : (s instanceof Player ? (Player) s : null);
        
        if (target == null) { s.sendMessage("§cTarget not found."); return true; }
        
        ItemStack reroll = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = reroll.getItemMeta();
        meta.setDisplayName("§b§lElemental Reroll");
        meta.setLore(Arrays.asList("§7Right-click to gamble!"));
        reroll.setItemMeta(meta);
        
        target.getInventory().addItem(reroll);
        s.sendMessage("§aGave reroll to " + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        return (args.length == 1) ? null : new ArrayList<>();
    }
}

class AdminElementHandler implements CommandExecutor, TabCompleter {
    private final ElementSMP plugin;
    public AdminElementHandler(ElementSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (s.isOp() && args.length >= 4 && args[0].equalsIgnoreCase("element") && args[1].equalsIgnoreCase("set")) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target != null) {
                String el = args[3].substring(0, 1).toUpperCase() + args[3].substring(1).toLowerCase();
                plugin.setPlayerElement(target, el);
                s.sendMessage("§aDone.");
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] args) {
        if (args.length == 1) return Arrays.asList("element");
        if (args.length == 2) return Arrays.asList("set");
        if (args.length == 3) return null;
        if (args.length == 4) return Arrays.asList(plugin.getElementList());
        return new ArrayList<>();
    }
}

class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled!"); } return true; } }
