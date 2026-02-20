package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    // Data Storage
    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility1 = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility2 = new HashMap<>();
    
    private final List<String> elementList = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Ocean", "Psychic", "Gravity");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        
        // Command Registration
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new AdminItemCommand());
        getCommand("give-chaos").setExecutor(new AdminItemCommand());
        
        AdminElementHandler adminHandler = new AdminElementHandler(this);
        getCommand("elementsmp").setExecutor(adminHandler);
        getCommand("elementsmp").setTabCompleter(adminHandler);

        // Core Loop: HUD, Particles, and Passives (Runs every 2 ticks)
        new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                angle += 0.15;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateActionBarHUD(p);
                    spawnElementalHalo(p, angle);
                    applyPassives(p);
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // --- ACTION BAR HUD LOGIC ---
    private void updateActionBarHUD(Player p) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind");
        
        String ab1Name = getAbilityName(el, 1);
        String ab1Status = getCooldownStatus(id, cdAbility1);
        
        String ab2Name = getAbilityName(el, 2);
        String ab2Status = getCooldownStatus(id, cdAbility2);

        // Format: ELEMENT | Ability 1: MM:SS | Ability 2: MM:SS
        String message = String.format("§b§l%s §8| §e%s: %s §8| §6%s: %s", 
                            el.toUpperCase(), ab1Name, ab1Status, ab2Name, ab2Status);
        
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private String getCooldownStatus(UUID id, HashMap<UUID, Long> map) {
        long time = map.getOrDefault(id, 0L) - System.currentTimeMillis();
        if (time <= 0) return "§a§lREADY";
        
        long totalSecs = time / 1000;
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        return String.format("§c%02d:%02d", mins, secs);
    }

    private String getAbilityName(String el, int slot) {
        return switch (el.toLowerCase()) {
            case "nature" -> (slot == 1) ? "Grapple" : "Overgrowth";
            case "gravity" -> (slot == 1) ? "Zero-G" : "Meteor";
            case "blood" -> (slot == 1) ? "Siphon" : "Rage";
            default -> "Power " + slot;
        };
    }

    // --- ABILITY LOGIC ---
    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toLowerCase();
        HashMap<UUID, Long> cdMap = (num == 1) ? cdAbility1 : cdAbility2;

        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            return;
        }

        boolean success = false;
        if (num == 1) {
            if (el.equals("nature")) { performNatureGrapple(p); success = true; cdMap.put(id, System.currentTimeMillis() + 15000); }
            // Add other Element 1s here
        } else {
            if (el.equals("gravity")) { performGravityMeteor(p); success = true; cdMap.put(id, System.currentTimeMillis() + 60000); }
            // Add other Element 2s here
        }

        if (success) p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1f);
    }

    private static void performNatureGrapple(Player p) {
        RayTraceResult result = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 30);
        if (result != null && result.getHitBlock() != null) {
            Location hit = result.getHitPosition().toLocation(p.getWorld());
            Vector dir = hit.toVector().subtract(p.getEyeLocation().toVector()).normalize();
            p.setVelocity(dir.multiply(2.2).setY(0.6));
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, hit, 20, 0.5, 0.5, 0.5, 0.1);
        }
    }

    private static void performGravityMeteor(Player p) {
        p.sendMessage("§7The sky darkens...");
        // Placeholder for Meteor logic
    }

    // --- REROLL SYSTEM ---
    @EventHandler
    public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
            e.setCancelled(true);
            boolean isChaos = item.getItemMeta().getDisplayName().contains("CHAOS");
            startRerollAnimation(e.getPlayer());
            item.setAmount(item.getAmount() - 1);
        }
    }

    public void startRerollAnimation(Player p) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 30) {
                    String finalEl = elementList.get(new Random().nextInt(elementList.size()));
                    playerElements.put(p.getUniqueId(), finalEl);
                    p.sendTitle("§6§lNEW ELEMENT", "§f" + finalEl, 10, 40, 10);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    this.cancel(); return;
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f + (ticks * 0.05f));
                ticks++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // --- UTILS & PASSIVES ---
    private void applyPassives(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase();
        if (el.equals("gravity")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, false, false));
    }

    private void spawnElementalHalo(Player p, double angle) {
        Location loc = p.getLocation().add(0, 2.1, 0);
        loc.add(Math.cos(angle) * 0.7, 0, Math.sin(angle) * 0.7);
        p.getWorld().spawnParticle(Particle.DUST, loc, 1, new Particle.DustOptions(Color.AQUA, 1f));
    }

    @EventHandler
    public void onFKey(PlayerSwapHandItemsEvent e) { 
        if (useHotkeys.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }
}

// --- COMMAND CLASSES ---
class AdminItemCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || !s.isOp()) return true;
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = star.getItemMeta();
        if (c.getName().contains("chaos")) {
            m.setDisplayName("§5§l« §d§lCHAOS REROLL §5§l»");
            m.setCustomModelData(1001);
        } else {
            m.setDisplayName("§b§l« §3§lELEMENTAL REROLL §b§l»");
            m.setCustomModelData(1000);
        }
        star.setItemMeta(m); p.getInventory().addItem(star); return true;
    }
}

class AdminElementHandler implements CommandExecutor, TabCompleter {
    private final ElementSMP plugin;
    public AdminElementHandler(ElementSMP plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (s.isOp() && args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            Player t = (args.length == 3) ? Bukkit.getPlayer(args[1]) : (s instanceof Player ? (Player)s : null);
            if (t != null) {
                ElementSMP.playerElements.put(t.getUniqueId(), args[args.length - 1]);
                s.sendMessage("§aElement set for " + t.getName());
            }
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender s, Command cmd, String a, String[] args) {
        if (args.length == 1) return List.of("set");
        if (args.length == 3) return Arrays.asList("Nature", "Gravity", "Fire", "Ice", "Blood");
        return null;
    }
}

class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys: " + (ElementSMP.useHotkeys.get(p.getUniqueId()) ? "§aON" : "§cOFF")); } return true; } }
