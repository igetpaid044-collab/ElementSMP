package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static HashMap<UUID, Long> djCooldown = new HashMap<>();
    private static final HashMap<UUID, Location> activeDomains = new HashMap<>();
    
    private final List<String> elements = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("elementsmp").setExecutor(new AdminElementHandler());
        getCommand("trust").setExecutor(new TrustHandler());
        getCommand("untrust").setExecutor(new TrustHandler());
        getCommand("give-reroll").setExecutor(new AdminItemCommand());
        getCommand("give-chaos").setExecutor(new AdminItemCommand());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateActionBarHUD(p);
                    handleDomainVision(p);
                    if (getElement(p).contains("wind") && !djCooldown.containsKey(p.getUniqueId())) p.setAllowFlight(true);
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase(); }

    private void handleDomainVision(Player p) {
        for (Map.Entry<UUID, Location> entry : activeDomains.entrySet()) {
            if (p.getWorld().equals(entry.getValue().getWorld()) && p.getLocation().distance(entry.getValue()) <= 10.5) {
                UUID casterId = entry.getKey();
                if (!p.getUniqueId().equals(casterId) && !trustedPlayers.getOrDefault(casterId, new HashSet<>()).contains(p.getUniqueId())) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 30, 0, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
                }
            }
        }
    }

    // --- SLOT MACHINE ANIMATION ---
    @EventHandler
    public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return;
        Player p = e.getPlayer();
        boolean isChaos = item.getItemMeta().getDisplayName().contains("Chaos");
        item.setAmount(item.getAmount() - 1);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks < 30) {
                    String roll = elements.get(new Random().nextInt(elements.size()));
                    p.sendTitle("§7Rolling...", (isChaos ? "§k" : "§f") + roll, 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + (ticks * 0.04f));
                } else {
                    String e1 = elements.get(new Random().nextInt(elements.size()));
                    if (isChaos) {
                        String e2; 
                        do { e2 = elements.get(new Random().nextInt(elements.size())); } while (e1.equals(e2));
                        playerElements.put(p.getUniqueId(), e1 + "-" + e2);
                        p.sendTitle("§5§lGLITCHED", "§d" + e1 + " §f& §5" + e2, 10, 60, 10);
                        p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 0.5f);
                    } else {
                        playerElements.put(p.getUniqueId(), e1);
                        p.sendTitle("§b§lNEW ELEMENT", "§f" + e1, 10, 40, 10);
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    }
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // --- THE OMNI-COMBO SYSTEM ---
    public static void triggerAbility(Player p, int n) {
        ElementSMP inst = (ElementSMP) Bukkit.getPluginManager().getPlugin("ElementSMP");
        String el = inst.getElement(p);

        if (n == 1 && el.contains("-")) {
            executeCombo(p, el);
            return;
        }

        if (n == 2 && (el.contains("void") || el.contains("ice") || el.contains("gravity"))) {
            inst.startDomain(p);
        } else {
            p.setVelocity(p.getLocation().getDirection().multiply(1.4).setY(0.3));
        }
    }

    private static void executeCombo(Player p, String el) {
        // STEAM (Fire + Water)
        if (el.contains("fire") && el.contains("water")) {
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 100, 3, 1, 3, 0.05);
            p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1f);
            for (Entity e : p.getNearbyEntities(5, 5, 5)) if (e instanceof LivingEntity t && e != p) { t.damage(4); t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2)); }
        }
        // STORM (Lightning + Water)
        else if (el.contains("lightning") && el.contains("water")) {
            p.getWorld().spawnParticle(Particle.DRIPPING_WATER, p.getLocation(), 100, 2, 2, 2);
            for (Entity e : p.getNearbyEntities(6, 6, 6)) if (e instanceof LivingEntity t && e != p) { p.getWorld().strikeLightning(t.getLocation()); t.damage(2); }
        }
        // SANDSTORM (Earth + Wind)
        else if (el.contains("earth") && el.contains("wind")) {
            p.getWorld().spawnParticle(Particle.BLOCK_DUST, p.getLocation(), 200, 4, 2, 4, 0.1, Material.SAND.createBlockData());
            for (Entity e : p.getNearbyEntities(7, 7, 7)) if (e instanceof LivingEntity t && e != p) t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 0));
        }
        // VOLCANO (Earth + Fire)
        else if (el.contains("earth") && el.contains("fire")) {
            p.getWorld().spawnParticle(Particle.LAVA, p.getLocation(), 50, 1, 1, 1);
            p.getLocation().getBlock().setType(Material.MAGMA_BLOCK);
            for (Entity e : p.getNearbyEntities(5, 5, 5)) if (e instanceof LivingEntity t && e != p) t.setFireTicks(100);
        }
        // MUD (Earth + Water)
        else if (el.contains("earth") && el.contains("water")) {
            for (Entity e : p.getNearbyEntities(5, 5, 5)) if (e instanceof LivingEntity t && e != p) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 5));
        }
        // BLOOD FREEZE (Blood + Ice)
        else if (el.contains("blood") && el.contains("ice")) {
            for (Entity e : p.getNearbyEntities(6, 6, 6)) if (e instanceof LivingEntity t && e != p) { t.damage(6); t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 10)); }
        }
        // BLACK HOLE (Gravity + Void)
        else if (el.contains("gravity") && el.contains("void")) {
            p.getWorld().spawnParticle(Particle.SQUID_INK, p.getLocation(), 300, 1, 1, 1, 0.02);
            for (Entity e : p.getNearbyEntities(12, 12, 12)) if (e instanceof LivingEntity t && e != p) t.setVelocity(p.getLocation().toVector().subtract(t.getLocation().toVector()).normalize().multiply(1.5));
        }
        // Default Glitch Dash
        else {
            p.setVelocity(p.getLocation().getDirection().multiply(2.5));
            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation(), 50, 0.5, 0.5, 0.5);
        }
    }

    public void startDomain(Player p) {
        Location loc = p.getLocation();
        activeDomains.put(p.getUniqueId(), loc);
        p.sendMessage("§d§lDOMAIN EXPANSION!");
        new BukkitRunnable() { @Override public void run() { activeDomains.remove(p.getUniqueId()); } }.runTaskLater(this, 300L);
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        for (Location center : activeDomains.values()) if (e.getTo().distance(center) > 10.5 && e.getFrom().distance(center) <= 10.5) e.setCancelled(true);
    }

    @EventHandler public void onJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (getElement(p).contains("wind") && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true); p.setAllowFlight(false);
            p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(1.0));
            djCooldown.put(p.getUniqueId(), System.currentTimeMillis() + 15000);
            new BukkitRunnable() { @Override public void run() { djCooldown.remove(p.getUniqueId()); } }.runTaskLater(this, 300L);
        }
    }

    @EventHandler public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            String el = getElement(p); if (el.contains("wind") || el.contains("gravity") || el.contains("void")) e.setCancelled(true);
        }
    }

    private void updateActionBarHUD(Player p) {
        String el = getElement(p).toUpperCase();
        long dj = djCooldown.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§5§l" + el + " §8| §fJump: " + (dj<=0?"§aREADY":"§c"+(dj/1000)+"s")));
    }
}

// SUPPORT CLASSES
class TrustHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || a.length == 0) return false;
        Player t = Bukkit.getPlayer(a[0]); if (t == null) return false;
        Set<UUID> set = ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (c.getName().equals("trust")) { set.add(t.getUniqueId()); p.sendMessage("§aTrusted " + t.getName()); }
        else { set.remove(t.getUniqueId()); p.sendMessage("§cUntrusted " + t.getName()); }
        return true;
    }
}
class AdminElementHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s.isOp() && a.length >= 3 && a[0].equalsIgnoreCase("set")) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t != null) { ElementSMP.playerElements.put(t.getUniqueId(), a[2]); s.sendMessage("§aElement set."); }
        }
        return true;
    }
}
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2") ? 2 : 1)); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys toggled."); } return true; } }
class AdminItemCommand implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p && s.isOp()) {
            ItemStack i = new ItemStack(Material.NETHER_STAR); ItemMeta m = i.getItemMeta();
            m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll");
            i.setItemMeta(m); p.getInventory().addItem(i);
        }
        return true;
    }
}
