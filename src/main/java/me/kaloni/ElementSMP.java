package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility1 = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility2 = new HashMap<>();
    public static HashMap<UUID, Long> cdDoubleJump = new HashMap<>();
    public static HashMap<UUID, Long> silencedPlayers = new HashMap<>();
    private final String[] elements = {"Wind", "Fire", "Water", "Earth", "Lightning", "Void"};

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new RerollCommand());
        
        // Registering the admin element command
        getCommand("elementsmp").setExecutor(new AdminElementHandler(this));

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) updateActionBarHUD(p);
        }, 0L, 20L);
    }

    // --- REROLL ANIMATION ---
    public void startRerollAnimation(Player p) {
        new BukkitRunnable() {
            int ticks = 0;
            final Random rand = new Random();
            @Override
            public void run() {
                if (ticks >= 100) {
                    String finalEl = elements[rand.nextInt(elements.length)];
                    setPlayerElement(p, finalEl);
                    p.sendTitle(getElementColor(finalEl) + "§l" + finalEl.toUpperCase(), "§7Your power has awakened!", 10, 70, 20);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    this.cancel();
                    return;
                }
                String randomEl = elements[rand.nextInt(elements.length)];
                p.sendTitle("§k||| " + getElementColor(randomEl) + randomEl.toUpperCase() + " §r§k|||", "§fRolling...", 0, 10, 0);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 2f);
                ticks++;
            }
        }.runTaskTimer(this, 0L, 4L); 
    }

    public void setPlayerElement(Player p, String element) {
        playerElements.put(p.getUniqueId(), element);
        if (element.equalsIgnoreCase("Wind")) p.setAllowFlight(true);
        else p.setAllowFlight(p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR);
    }

    @EventHandler
    public void onRerollUse(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
            if (item.getItemMeta().getDisplayName().contains("Reroll")) {
                e.setCancelled(true);
                item.setAmount(item.getAmount() - 1);
                startRerollAnimation(e.getPlayer());
            }
        }
    }

    // --- HUD LOGIC ---
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

    // --- ABILITY TRIGGER ---
    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        if (silencedPlayers.getOrDefault(id, 0L) > System.currentTimeMillis()) return;
        String e = playerElements.getOrDefault(id, "Wind");
        HashMap<UUID, Long> cdMap = (num == 1) ? cdAbility1 : cdAbility2;
        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) return;

        if (num == 1) { // 40s CD
            handlePrimary(p, e);
            cdMap.put(id, System.currentTimeMillis() + 40000);
        } else { // 60s CD (Void 2m)
            handleSecondary(p, e);
            cdMap.put(id, System.currentTimeMillis() + (e.equalsIgnoreCase("void") ? 120000 : 60000));
        }
    }

    private static void handlePrimary(Player p, String e) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§lUSED: " + e.toUpperCase() + " A1"));
        for (Entity target : p.getNearbyEntities(6, 6, 6)) {
            if (target instanceof LivingEntity le && !target.equals(p)) {
                switch (e.toLowerCase()) {
                    case "fire" -> { le.setFireTicks(100); le.damage(6.0, p); }
                    case "water" -> { le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5)); le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2)); }
                    case "lightning" -> { le.getWorld().strikeLightning(le.getLocation()); le.damage(4.0, p); }
                    case "void" -> { le.setVelocity(p.getLocation().toVector().subtract(le.getLocation().toVector()).normalize().multiply(1.5)); le.damage(6.0, p); }
                    case "earth" -> { le.damage(6.0, p); le.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0)); }
                    default -> { le.setVelocity(new Vector(0, 1.2, 0)); le.damage(6.0, p); }
                }
            }
        }
    }

    private static void handleSecondary(Player p, String e) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§lUSED: " + e.toUpperCase() + " A2"));
        if (e.equalsIgnoreCase("void")) {
            for (Entity near : p.getNearbyEntities(20, 20, 20)) {
                if (near instanceof Player target && !target.equals(p)) silencedPlayers.put(target.getUniqueId(), System.currentTimeMillis() + 30000);
            }
        } else if (e.equalsIgnoreCase("fire")) {
            p.setVelocity(p.getLocation().getDirection().multiply(2.5).setY(0.2));
        } else if (e.equalsIgnoreCase("earth")) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 255));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 255));
        } else {
            p.setVelocity(p.getLocation().getDirection().multiply(2.2).setY(0.2));
        }
    }

    @EventHandler public void onFKey(PlayerSwapHandItemsEvent event) { if (useHotkeys.getOrDefault(event.getPlayer().getUniqueId(), false)) { event.setCancelled(true); triggerAbility(event.getPlayer(), event.getPlayer().isSneaking() ? 2 : 1); } }
    @EventHandler public void onJoin(PlayerJoinEvent event) { playerElements.putIfAbsent(event.getPlayer().getUniqueId(), "Wind"); if (playerElements.get(event.getPlayer().getUniqueId()).equals("Wind")) event.getPlayer().setAllowFlight(true); }
}

// --- COMMAND CLASSES ---

class AdminElementHandler implements CommandExecutor {
    private final ElementSMP plugin;
    public AdminElementHandler(ElementSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!s.isOp()) { s.sendMessage("§cNo permission."); return true; }
        
        // Command: /elementsmp element set <player> <element>
        if (args.length >= 4 && args[0].equalsIgnoreCase("element") && args[1].equalsIgnoreCase("set")) {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null) { s.sendMessage("§cPlayer not found."); return true; }
            
            String newElement = args[3].substring(0, 1).toUpperCase() + args[3].substring(1).toLowerCase();
            plugin.setPlayerElement(target, newElement);
            
            s.sendMessage("§aSet " + target.getName() + "'s element to " + newElement);
            target.sendMessage("§eAn admin has set your element to: " + plugin.getElementColor(newElement) + "§l" + newElement);
            return true;
        }
        s.sendMessage("§cUsage: /elementsmp element set <player> <element>");
        return true;
    }
}

class RerollCommand implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p && p.isOp()) {
            ItemStack reroll = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = reroll.getItemMeta();
            meta.setDisplayName("§b§lElemental Reroll");
            meta.setLore(Arrays.asList("§7Right-click to gamble", "§7for a new element!"));
            reroll.setItemMeta(meta);
            p.getInventory().addItem(reroll);
            p.sendMessage("§aYou received an Elemental Reroll!");
        }
        return true;
    }
}

class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled!"); } return true; } }
