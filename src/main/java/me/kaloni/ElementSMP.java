package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static HashMap<UUID, Long> djCooldown = new HashMap<>();
    private static final HashMap<UUID, Location> activeDomains = new HashMap<>();

    private final List<String> elements = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("elementsmp").setExecutor(new AdminHandler());
        getCommand("trust").setExecutor(new TrustHandler());
        getCommand("give-reroll").setExecutor(new ItemHandler());
        getCommand("give-chaos").setExecutor(new ItemHandler());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    checkDomain(p);
                    if (getElement(p).contains("wind") && !djCooldown.containsKey(p.getUniqueId())) p.setAllowFlight(true);
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase(); }

    // --- ABILITY CORE ---
    public static void triggerAbility(Player p, int slot) {
        ElementSMP plugin = (ElementSMP) Bukkit.getPluginManager().getPlugin("ElementSMP");
        String el = plugin.getElement(p);

        // FUSION CHECK (Only for Chaos users)
        if (el.contains("-") && slot == 1) {
            handleFusion(p, el);
            return;
        }

        // Standard 2-Ability System
        switch (el.split("-")[0]) { // Use first element if dual
            case "fire" -> {
                if (slot == 1) { p.setFireTicks(0); p.setVelocity(p.getLocation().getDirection().multiply(2)); p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.1); }
                else { for (Entity e : p.getNearbyEntities(5, 5, 5)) if (e instanceof LivingEntity t) t.setFireTicks(100); }
            }
            case "water" -> {
                if (slot == 1) { p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 100, 1)); p.getWorld().spawnParticle(Particle.BUBBLE, p.getLocation(), 50, 1, 1, 1); }
                else { for (Entity e : p.getNearbyEntities(5, 5, 5)) e.setVelocity(p.getLocation().getDirection().multiply(-1.5)); }
            }
            case "void" -> {
                if (slot == 1) { p.teleport(p.getTargetBlockExact(15).getLocation()); p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f); }
                else { plugin.startDomain(p); }
            }
            case "ice" -> {
                if (slot == 1) { p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 50, 1, 1, 1); for (Entity e : p.getNearbyEntities(4, 4, 4)) if (e instanceof LivingEntity t) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5)); }
                else { plugin.startDomain(p); }
            }
            default -> p.setVelocity(p.getLocation().getDirection().multiply(1.2).setY(0.4));
        }
    }

    private static void handleFusion(Player p, String el) {
        if (el.contains("fire") && el.contains("water")) { // STEAM
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 200, 3, 2, 3, 0.05);
            for (Entity e : p.getNearbyEntities(6, 6, 6)) if (e instanceof LivingEntity t && e != p) { t.damage(5); t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0)); }
        } else if (el.contains("lightning") && el.contains("water")) { // STORM
            p.getWorld().strikeLightning(p.getLocation());
            for (Entity e : p.getNearbyEntities(8, 8, 8)) if (e instanceof LivingEntity t) t.damage(6);
        } else { // GLITCH DASH
            p.setVelocity(p.getLocation().getDirection().multiply(3));
            p.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, p.getLocation(), 100, 0.5, 0.5, 0.5, 0.2);
        }
    }

    // --- DOMAIN SYSTEM (WITH BLACK VISION) ---
    public void startDomain(Player p) {
        activeDomains.put(p.getUniqueId(), p.getLocation());
        p.sendMessage("§d§lDOMAIN EXPANSION!");
        new BukkitRunnable() { @Override public void run() { activeDomains.remove(p.getUniqueId()); } }.runTaskLater(this, 300L);
    }

    private void checkDomain(Player p) {
        for (Map.Entry<UUID, Location> entry : activeDomains.entrySet()) {
            if (p.getWorld().equals(entry.getValue().getWorld()) && p.getLocation().distance(entry.getValue()) <= 10.5) {
                if (!p.getUniqueId().equals(entry.getKey()) && !trustedPlayers.getOrDefault(entry.getKey(), new HashSet<>()).contains(p.getUniqueId())) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                }
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        for (Location loc : activeDomains.values()) {
            if (e.getTo().distance(loc) > 10.5 && e.getFrom().distance(loc) <= 10.5) e.setCancelled(true);
        }
    }

    // --- REROLL ANIMATION ---
    @EventHandler
    public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.NETHER_STAR) return;
        Player p = e.getPlayer();
        boolean isChaos = item.getItemMeta().getDisplayName().contains("Chaos");
        item.setAmount(item.getAmount() - 1);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks < 25) {
                    p.sendTitle("§7Rolling...", (isChaos ? "§k" : "§f") + elements.get(new Random().nextInt(elements.size())), 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + (ticks * 0.05f));
                } else {
                    String e1 = elements.get(new Random().nextInt(elements.size()));
                    if (isChaos) {
                        String e2 = elements.get(new Random().nextInt(elements.size()));
                        playerElements.put(p.getUniqueId(), e1 + "-" + e2);
                        p.sendTitle("§5§lGLITCHED", "§d" + e1 + " §f& §5" + e2, 10, 60, 10);
                    } else {
                        playerElements.put(p.getUniqueId(), e1);
                        p.sendTitle("§b§lELEMENT", "§f" + e1, 10, 40, 10);
                    }
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void updateHUD(Player p) {
        String el = getElement(p).toUpperCase();
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b" + el + " §8| §fRight-Click Star to Reroll"));
    }
}

// Support Classes
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2") ? 2 : 1)); return true; } }
class TrustHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && a.length > 0) { Player t = Bukkit.getPlayer(a[0]); if (t != null) ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(t.getUniqueId()); p.sendMessage("§aTrusted!"); } return true; } }
class AdminHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s.isOp() && a.length >= 2) { Player t = Bukkit.getPlayer(a[0]); if (t != null) ElementSMP.playerElements.put(t.getUniqueId(), a[1]); } return true; } }
class ItemHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && s.isOp()) { ItemStack i = new ItemStack(Material.NETHER_STAR); ItemMeta m = i.getItemMeta(); m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll"); i.setItemMeta(m); p.getInventory().addItem(i); } return true; } }
