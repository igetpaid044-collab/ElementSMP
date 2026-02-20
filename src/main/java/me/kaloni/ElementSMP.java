package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
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
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    private static ElementSMP instance;
    public static ElementSMP getInstance() { return instance; }

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static HashMap<UUID, Long> cd1 = new HashMap<>();
    public static HashMap<UUID, Long> cd2 = new HashMap<>();
    public static HashMap<UUID, Long> doubleJumpTimer = new HashMap<>();
    
    private final List<String> elementList = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity");

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new AdminItemCommand());
        getCommand("elementsmp").setExecutor(new AdminElementHandler());
        getCommand("trust").setExecutor(new TrustHandler());
        getCommand("untrust").setExecutor(new TrustHandler());

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateActionBarHUD(p);
                    // Passive double jump check
                    if (getElement(p).equals("wind") && !p.getAllowFlight()) p.setAllowFlight(true);
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase(); }

    // --- PASSIVE: NO FALL DAMAGE & DOUBLE JUMP ---
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            String el = getElement(p);
            if (el.equals("wind") || el.equals("gravity") || el.equals("void")) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (getElement(p).equals("wind") && p.getGameMode() != GameMode.CREATIVE) {
            e.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);
            p.setVelocity(p.getLocation().getDirection().multiply(1.2).setY(1.0));
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.2, 0.2, 0.2, 0.1);
            p.playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
            
            // 15 second cooldown for double jump
            new BukkitRunnable() {
                @Override public void run() { if (p.isOnline()) p.setAllowFlight(true); }
            }.runTaskLater(this, 300L); // 15 seconds
        }
    }

    // --- DOMAIN EXPANSION LOGIC (Correctly Coded for 1.21) ---
    public void spawnDomain(Player caster, Material barrierType, int radius) {
        Location center = caster.getLocation();
        List<Block> changedBlocks = new ArrayList<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    if (loc.distance(center) > radius - 1 && loc.distance(center) <= radius) {
                        caster.sendBlockChange(loc, barrierType.createBlockData());
                        changedBlocks.add(loc.getBlock());
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block b : changedBlocks) caster.sendBlockChange(b.getLocation(), b.getBlockData());
            }
        }.runTaskLater(this, 200L); // 10 seconds duration
    }

    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String el = instance.getElement(p);
        HashMap<UUID, Long> cdMap = (num == 1) ? cd1 : cd2;

        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) return;

        boolean success = false;
        if (num == 1) { // Tactical
            if (el.equals("void")) { p.teleport(p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(8))); success = true; }
            if (el.equals("ice")) { p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2)); success = true; }
        } else { // Domain / Ultimate
            if (el.equals("void")) { instance.spawnDomain(p, Material.BLACK_STAINED_GLASS, 10); success = true; }
            if (el.equals("ice")) { instance.spawnDomain(p, Material.ICE, 8); success = true; }
        }

        if (success) cdMap.put(id, System.currentTimeMillis() + (num == 1 ? 10000 : 60000));
    }

    private void updateActionBarHUD(Player p) {
        String el = getElement(p).toUpperCase();
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b§l" + el + " §8| §fDouble Jump: §aREADY"));
    }

    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) {
        if (useHotkeys.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }
}

// --- NEW COMMAND HANDLERS ---

class TrustHandler implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || a.length == 0) return false;
        Player target = Bukkit.getPlayer(a[0]);
        if (target == null) return false;

        Set<UUID> trusted = ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (c.getName().equalsIgnoreCase("trust")) {
            trusted.add(target.getUniqueId());
            p.sendMessage("§aYou now trust " + target.getName());
        } else {
            trusted.remove(target.getUniqueId());
            p.sendMessage("§cYou no longer trust " + target.getName());
        }
        return true;
    }
}

class AdminElementHandler implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.isOp()) return true;
        if (a.length >= 3 && a[0].equalsIgnoreCase("set")) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t != null) {
                ElementSMP.playerElements.put(t.getUniqueId(), a[2]);
                s.sendMessage("§aSet " + t.getName() + " to " + a[2]);
            }
        }
        return true;
    }
}
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled."); } return true; } }
class AdminItemCommand implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && s.isOp()) { ItemStack star = new ItemStack(Material.NETHER_STAR); ItemMeta m = star.getItemMeta(); m.setDisplayName("§b§lElemental Reroll"); star.setItemMeta(m); p.getInventory().addItem(star); } return true; } }
