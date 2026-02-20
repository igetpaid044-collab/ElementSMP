package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashMap;
import java.util.UUID;

public class ElementSMP extends JavaPlugin implements Listener {

    // --- Data Storage ---
    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Long> cooldowns = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityLogic());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("elements").setExecutor(new PowerMenu());
        
        // --- PASSIVE MANAGER (Runs every 5 seconds) ---
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                applyPassives(p);
            }
        }, 0L, 100L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        playerElements.putIfAbsent(p.getUniqueId(), "Void");
        useHotkeys.putIfAbsent(p.getUniqueId(), false);
    }

    // --- HOTKEY HANDLER (Offhand/Shift+F) ---
    @EventHandler
    public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (useHotkeys.getOrDefault(p.getUniqueId(), false)) {
            event.setCancelled(true);
            if (p.isSneaking()) executeAbility(p, 2);
            else executeAbility(p, 1);
        }
    }

    // --- COOLDOWN & ABILITY MANAGER ---
    public static void executeAbility(Player p, int num) {
        String e = playerElements.getOrDefault(p.getUniqueId(), "Void");
        String icon = getIcon(e);
        
        long timeLeft = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        if (timeLeft > 0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§l" + icon + " COOLDOWN: " + (timeLeft / 1000) + "s"));
            return;
        }

        String abilityName = (num == 1) ? getAbilityName(e, 1) : getAbilityName(e, 2);
        double damage = (e.equals("Void") && num == 1) ? 12.0 : 6.0;

        p.sendMessage("§a§l" + icon + " Ability Used: §f" + abilityName);
        
        for (Entity target : p.getNearbyEntities(8, 8, 8)) {
            if (target instanceof LivingEntity && !target.equals(p)) {
                ((LivingEntity) target).damage(damage, p);
                spawnAbilityEffects(target.getLocation(), e);
                break;
            }
        }

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§l" + icon + " " + abilityName.toUpperCase()));
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + 15000);
    }

    // --- PARTICLES & EFFECTS ---
    private static void spawnAbilityEffects(Location loc, String element) {
        if (element.equals("Void")) {
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 100, 0.5, 1, 0.5, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        } else {
            loc.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, loc, 1);
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
        }
    }

    // --- PASSIVES SYSTEM ---
    private void applyPassives(Player p) {
        String e = playerElements.get(p.getUniqueId());
        if (e == null) return;

        switch (e) {
            case "Void":
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0));
                break;
            case "Fire":
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0));
                break;
        }
    }

    public static String getIcon(String e) { return e.equals("Void") ? "§5[?]" : "§f[*]"; }
    private static String getAbilityName(String e, int n) { 
        if (e.equals("Void")) return n == 1 ? "Singularity" : "Void Rift";
        return n == 1 ? "Strike" : "Dash";
    }
}

// --- COMMAND CLASSES ---
class AbilityLogic implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) return true;
        int num = (a.length > 0 && a[0].equals("2")) ? 2 : 1;
        ElementSMP.executeAbility((Player) s, num);
        return true;
    }
}

class ControlToggle implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) return true;
        Player p = (Player) s;
        boolean cur = Element
