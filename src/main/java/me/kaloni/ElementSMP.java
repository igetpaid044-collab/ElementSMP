package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
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

    private static ElementSMP instance;
    public static ElementSMP getInstance() { return instance; }

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Long> cd1 = new HashMap<>();
    public static HashMap<UUID, Long> cd2 = new HashMap<>();
    
    private final List<String> elementList = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity");

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new AdminItemCommand());
        getCommand("give-chaos").setExecutor(new AdminItemCommand());
        getCommand("elementsmp").setExecutor(new AdminElementHandler());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) updateActionBarHUD(p);
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private static PotionEffectType getSafePotion(String... names) {
        for (String name : names) {
            @SuppressWarnings("deprecation")
            PotionEffectType type = PotionEffectType.getByName(name);
            if (type != null) return type;
        }
        return null;
    }

    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toLowerCase();
        HashMap<UUID, Long> cdMap = (num == 1) ? cd1 : cd2;

        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            p.sendMessage("§cWait " + (cdMap.get(id) - System.currentTimeMillis())/1000 + "s");
            return;
        }

        boolean success = (num == 1) ? executeAbility1(p, el) : executeAbility2(p, el);

        if (success) {
            long cooldown = getInstance().getConfig().getLong("cooldowns.a" + num, (num == 1 ? 10 : 45)) * 1000;
            cdMap.put(id, System.currentTimeMillis() + cooldown);
        }
    }

    private static boolean executeAbility1(Player p, String el) {
        switch (el) {
            case "wind":
                p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(1.0));
                p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
                return true;
            case "fire":
                p.setVelocity(p.getLocation().getDirection().multiply(2.0));
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 20, 0.2, 0.2, 0.2, 0.1);
                return true;
            case "void":
                p.teleport(p.getEyeLocation().add(p.getLocation().getDirection().multiply(8)));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                return true;
            case "lightning":
                p.addPotionEffect(new PotionEffect(getSafePotion("SPEED"), 100, 3));
                return true;
            case "ice":
                p.addPotionEffect(new PotionEffect(getSafePotion("RESISTANCE", "DAMAGE_RESISTANCE"), 100, 2));
                return true;
            case "water":
                p.addPotionEffect(new PotionEffect(getSafePotion("DOLPHINS_GRACE"), 200, 1));
                return true;
            case "earth":
                p.addPotionEffect(new PotionEffect(getSafePotion("ABSORPTION"), 200, 1));
                return true;
            case "nature":
                RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 20);
                if (res != null) {
                    p.setVelocity(res.getHitPosition().toLocation(p.getWorld()).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(1.8));
                    return true;
                }
                return false;
            case "blood":
                p.damage(2.0);
                p.addPotionEffect(new PotionEffect(getSafePotion("SPEED"), 120, 4));
                return true;
            case "gravity":
                p.addPotionEffect(new PotionEffect(getSafePotion("LEVITATION"), 30, 1));
                return true;
            default: return false;
        }
    }

    private static boolean executeAbility2(Player p, String el) {
        switch (el) {
            case "fire":
                for (Entity e : p.getNearbyEntities(6, 6, 6)) if (e instanceof LivingEntity t && t != p) { t.setFireTicks(100); t.setVelocity(t.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5)); }
                return true;
            case "ice":
                PotionEffectType slow = getSafePotion("SLOWNESS", "SLOW");
                for (Entity e : p.getNearbyEntities(8, 8, 8)) if (e instanceof LivingEntity t && t != p && slow != null) t.addPotionEffect(new PotionEffect(slow, 140, 10));
                return true;
            case "lightning":
                Entity targetLt = getTarget(p, 20);
                if (targetLt != null) { p.getWorld().strikeLightning(targetLt.getLocation()); return true; }
                return false;
            case "void":
                PotionEffectType dark = getSafePotion("DARKNESS", "BLINDNESS");
                for (Entity e : p.getNearbyEntities(15, 15, 15)) if (e instanceof LivingEntity t && t != p && dark != null) t.addPotionEffect(new PotionEffect(dark, 200, 0));
                return true;
            case "water":
                for (Entity e : p.getNearbyEntities(10, 5, 10)) if (e instanceof LivingEntity t && t != p) t.setVelocity(p.getLocation().getDirection().multiply(2.5).setY(0.5));
                return true;
            case "earth":
                for (Entity e : p.getNearbyEntities(8, 3, 8)) if (e instanceof LivingEntity t && t != p) { t.damage(6.0); t.setVelocity(new Vector(0, 1.2, 0)); }
                return true;
            case "nature":
                Entity targetNat = getTarget(p, 15);
                if (targetNat instanceof LivingEntity t) { t.addPotionEffect(new PotionEffect(getSafePotion("WITHER"), 100, 1)); return true; }
                return false;
            case "blood":
                Entity targetBld = getTarget(p, 10);
                if (targetBld instanceof LivingEntity t) { t.damage(6.0); p.setHealth(Math.min(p.getHealth() + 4.0, p.getHealth())); return true; }
                return false;
            case "gravity":
                for (Entity e : p.getNearbyEntities(12, 12, 12)) if (e instanceof LivingEntity t && t != p) t.setVelocity(p.getLocation().toVector().subtract(t.getLocation().toVector()).normalize().multiply(1.5));
                return true;
            case "wind":
                for (Entity e : p.getNearbyEntities(10, 10, 10)) if (e instanceof LivingEntity t && t != p) t.setVelocity(new Vector(0, 2.5, 0));
                return true;
            default: return false;
        }
    }

    private static Entity getTarget(Player p, int range) {
        List<Entity> nearby = p.getNearbyEntities(range, range, range);
        for (Entity e : nearby) if (e instanceof LivingEntity && p.hasLineOfSight(e)) return e;
        return null;
    }

    private void updateActionBarHUD(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind").toUpperCase();
        long c1 = (cd1.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis()) / 1000;
        long c2 = (cd2.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis()) / 1000;
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b§l" + el + " §8| §eA1: " + (c1<=0?"§aREADY":"§c"+c1+"s") + " §8| §6A2: " + (c2<=0?"§aREADY":"§c"+c2+"s")));
    }

    @EventHandler
    public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Reroll")) {
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            String newEl = elementList.get(new Random().nextInt(elementList.size()));
            playerElements.put(e.getPlayer().getUniqueId(), newEl);
            e.getPlayer().sendMessage("§a§lElement Rerolled to: §b§l" + newEl);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (useHotkeys.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }
}

// Support classes unchanged
class AdminElementHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s.isOp() && a.length >= 2) { Player t = Bukkit.getPlayer(a[1]); if (t != null) { ElementSMP.playerElements.put(t.getUniqueId(), a[2]); s.sendMessage("§aSet " + t.getName() + " to " + a[2]); } } return true; } }
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled."); } return true; } }
class AdminItemCommand implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && s.isOp()) { ItemStack star = new ItemStack(Material.NETHER_STAR); ItemMeta m = star.getItemMeta(); m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll"); star.setItemMeta(m); p.getInventory().addItem(star); } return true; } }
