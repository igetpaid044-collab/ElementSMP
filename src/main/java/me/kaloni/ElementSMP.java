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
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    
    public static HashMap<UUID, Long> cdAbility1 = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility2 = new HashMap<>();
    public static HashMap<UUID, Long> cdDoubleJump = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());

        // Dynamic HUD Ticker
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                updateActionBarHUD(p);
            }
        }, 0L, 20L);
    }

    private void updateActionBarHUD(Player p) {
        long now = System.currentTimeMillis();
        long cd1 = cdAbility1.getOrDefault(p.getUniqueId(), 0L) - now;
        long cd2 = cdAbility2.getOrDefault(p.getUniqueId(), 0L) - now;

        // If an ability was JUST used or is on cooldown, show that instead of the HUD
        if (cd1 > 38000 || cd2 > 58000) return; // Brief pause to show "USED" message
        
        if (cd1 > 0 || cd2 > 0) {
            String msg = "";
            if (cd1 > 0) msg += "§6A1: §f" + (cd1 / 1000) + "s ";
            if (cd2 > 0) msg += "§eA2: §f" + (cd2 / 1000) + "s";
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg.trim()));
        } else {
            // Normal Element HUD
            String el = playerElements.getOrDefault(p.getUniqueId(), "Wind");
            String color = getElementColor(el);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§7Element: " + color + "§l" + el.toUpperCase()));
        }
    }

    private String getElementColor(String el) {
        return switch (el.toLowerCase()) {
            case "fire", "magma" -> "§c";
            case "water", "ice" -> "§3";
            case "earth", "nature" -> "§2";
            case "lightning" -> "§e";
            case "void", "shadow" -> "§5";
            case "light" -> "§f";
            default -> "§b"; // Wind/Air
        };
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        playerElements.putIfAbsent(p.getUniqueId(), "Wind");
        if (playerElements.get(p.getUniqueId()).equals("Wind")) p.setAllowFlight(true);
    }

    // --- WIND PASSIVES: Double Jump (20s CD) & No Fall Damage ---
    @EventHandler
    public void onMove(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (playerElements.getOrDefault(p.getUniqueId(), "").equals("Wind")) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;
            
            event.setCancelled(true);
            p.setAllowFlight(false);
            p.setFlying(false);

            long timeLeft = cdDoubleJump.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
            if (timeLeft > 0) {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                return;
            }

            p.setVelocity(p.getLocation().getDirection().multiply(1.1).setY(0.9));
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
            p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.5f);
            
            cdDoubleJump.put(p.getUniqueId(), System.currentTimeMillis() + 20000);
        }
    }

    @EventHandler
    public void onGroundCheck(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (playerElements.getOrDefault(p.getUniqueId(), "").equals("Wind") && p.isOnGround() && !p.getAllowFlight()) {
            p.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL && playerElements.getOrDefault(p.getUniqueId(), "").equals("Wind")) {
                event.setCancelled(true);
            }
        }
    }

    public static void triggerAbility(Player p, int num) {
        String e = playerElements.getOrDefault(p.getUniqueId(), "Wind");
        HashMap<UUID, Long> targetCDMap = (num == 1) ? cdAbility1 : cdAbility2;
        long timeLeft = targetCDMap.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();

        if (timeLeft > 0) return; // Ticker handles the message

        if (e.equalsIgnoreCase("Wind")) {
            if (num == 1) { // Ability 1: TORNADO (40s CD)
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§lUSED: WIND TORNADO"));
                for (Entity target : p.getNearbyEntities(5, 5, 5)) {
                    if (target instanceof LivingEntity le && !target.equals(p)) {
                        le.setVelocity(new Vector(0, 1.2, 0));
                        le.damage(6.0, p); 
                        Location loc = le.getLocation();
                        for (int i = 0; i < 4; i++) {
                            double y = i * 0.5;
                            for (double angle = 0; angle < 360; angle += 45) {
                                double x = Math.cos(Math.toRadians(angle)) * 0.8;
                                double z = Math.sin(Math.toRadians(angle)) * 0.8;
                                loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(x, y, z), 1, 0, 0, 0, 0);
                            }
                        }
                    }
                }
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 0.8f);
                targetCDMap.put(p.getUniqueId(), System.currentTimeMillis() + 40000);
            } else { // Ability 2: DASH (60s CD)
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§lUSED: WIND DASH"));
                Vector dir = p.getLocation().getDirection().normalize();
                p.setVelocity(dir.multiply(2.2).setY(0.2));
                for (Entity target : p.getNearbyEntities(3, 3, 3)) {
                    if (target instanceof LivingEntity le && !target.equals(p)) {
                        le.damage(4.0, p);
                    }
                }
                p.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, p.getLocation(), 1);
                p.playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1f, 2f);
                targetCDMap.put(p.getUniqueId(), System.currentTimeMillis() + 60000);
            }
        }
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
        if (s instanceof Player p) {
            ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1);
        }
        return true;
    }
}

class ControlToggle implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) {
            UUID id = p.getUniqueId();
            boolean cur = ElementSMP.useHotkeys.getOrDefault(id, false);
            ElementSMP.useHotkeys.put(id, !cur);
            p.sendMessage("§bHotkeys (F): " + (!cur ? "§aEnabled" : "§cDisabled"));
        }
        return true;
    }
}
