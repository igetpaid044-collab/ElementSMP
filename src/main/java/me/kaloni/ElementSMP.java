package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Long> cooldowns = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    private final HashMap<UUID, Long> jumpCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        playerElements.putIfAbsent(p.getUniqueId(), "Wind");
        if (playerElements.get(p.getUniqueId()).equals("Wind")) {
            p.setAllowFlight(true); // Required for Double Jump logic
        }
    }

    // --- WIND PASSIVES: Double Jump & No Fall Damage ---
    @EventHandler
    public void onMove(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (playerElements.getOrDefault(p.getUniqueId(), "").equals("Wind")) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
            
            event.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);

            // Double Jump Velocity
            p.setVelocity(p.getLocation().getDirection().multiply(1.2).setY(1.0));
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.5, 0.1, 0.5, 0.05);
            p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.5f);
        }
    }

    @EventHandler
    public void onGroundCheck(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (playerElements.getOrDefault(p.getUniqueId(), "").equals("Wind")) {
            if (p.isOnGround() && !p.getAllowFlight()) {
                p.setAllowFlight(true);
            }
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                if (playerElements.getOrDefault(p.getUniqueId(), "").equals("Wind")) {
                    event.setCancelled(true);
                    p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.2, 0.1, 0.2, 0.02);
                }
            }
        }
    }

    // --- ABILITIES ---
    public static void triggerAbility(Player p, int num) {
        String e = playerElements.getOrDefault(p.getUniqueId(), "Wind");
        long timeLeft = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();

        if (timeLeft > 0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§lCOOLDOWN: " + (timeLeft / 1000) + "s"));
            return;
        }

        if (e.equals("Wind")) {
            if (num == 1) { // Ability 1: Launch
                for (Entity target : p.getNearbyEntities(5, 5, 5)) {
                    if (target instanceof LivingEntity && !target.equals(p)) {
                        target.setVelocity(new Vector(0, 1.5, 0));
                        p.getWorld().spawnParticle(Particle.CLOUD, target.getLocation(), 30, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
            } else { // Ability 2: Wind Dash
                p.setVelocity(p.getLocation().getDirection().multiply(2.5).setY(0.2));
                p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1f, 2f);
            }
        }

        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + 7000); // 7s cooldown
    }

    @EventHandler
    public void onFKey(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (useHotkeys.getOrDefault(p.getUniqueId(), false)) {
            event.setCancelled(true);
            triggerAbility(p, p.isSneaking() ? 2 : 1);
        }
    }
}

class AbilityHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player) {
            int n = (a.length > 0 && a[0].equals("2")) ? 2 : 1;
            ElementSMP.triggerAbility((Player) s, n);
        }
        return true;
    }
}

class ControlToggle implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player) {
            UUID id = ((Player) s).getUniqueId();
            boolean cur = ElementSMP.useHotkeys.getOrDefault(id, false);
            ElementSMP.useHotkeys.put(id, !cur);
            s.sendMessage("§bWind Controls: " + (!cur ? "§aF-Key Enabled" : "§cCommands Only"));
        }
        return true;
    }
}
