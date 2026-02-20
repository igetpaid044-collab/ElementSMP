package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Long> cooldowns = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("elements").setExecutor(new InfoMenu());

        // PASSIVE TICKER (Every 5 seconds)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) applyPassives(p);
        }, 0L, 100L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        playerElements.putIfAbsent(p.getUniqueId(), "Void");
        useHotkeys.putIfAbsent(p.getUniqueId(), false);
    }

    @EventHandler
    public void onOffhandSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (useHotkeys.getOrDefault(p.getUniqueId(), false)) {
            event.setCancelled(true);
            triggerAbility(p, p.isSneaking() ? 2 : 1);
        }
    }

    public static void triggerAbility(Player p, int num) {
        String e = playerElements.getOrDefault(p.getUniqueId(), "Void");
        long timeLeft = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();

        if (timeLeft > 0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§lCOOLDOWN: " + (timeLeft / 1000) + "s"));
            return;
        }

        String name = (num == 1) ? getAbility1(e) : getAbility2(e);
        double dmg = (e.equals("Void") && num == 1) ? 12.0 : 6.0;

        p.sendMessage("§a§l" + getIcon(e) + " Used: §f" + name);
        
        for (Entity target : p.getNearbyEntities(7, 7, 7)) {
            if (target instanceof LivingEntity && !target.equals(p)) {
                ((LivingEntity) target).damage(dmg, p);
                playEffects(target.getLocation(), e);
                break;
            }
        }

        cooldowns.put(p.getUniqueId(), System.currentTimeMillis() + 15000);
    }

    private static void playEffects(Location l, String e) {
        if (e.equals("Void")) {
            l.getWorld().spawnParticle(Particle.REVERSE_PORTAL, l, 60, 0.5, 1, 0.5, 0.1);
            l.getWorld().playSound(l, Sound.ENTITY_WARDEN_SONIC_BOOM, 1, 1);
        } else {
            l.getWorld().spawnParticle(Particle.FLAME, l, 30, 0.3, 0.3, 0.3, 0.05);
            l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
        }
    }

    private void applyPassives(Player p) {
        String e = playerElements.get(p.getUniqueId());
        if (e == null) return;
        if (e.equals("Void")) p.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 120, 0));
        if (e.equals("Fire")) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0));
    }

    public static String getIcon(String e) { return e.equals("Void") ? "🌌" : "✨"; }
    private static String getAbility1(String e) { return e.equals("Void") ? "Singularity" : "Pulse"; }
    private static String getAbility2(String e) { return e.equals("Void") ? "Abyssal Rift" : "Burst"; }
}

class AbilityHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player) ElementSMP.triggerAbility((Player) s, (a.length > 0 && a[0].equals("2")) ? 2 : 1);
        return true;
    }
}

class ControlToggle implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) return true;
        UUID id = ((Player) s).getUniqueId();
        boolean cur = ElementSMP.useHotkeys.getOrDefault(id, false);
        ElementSMP.useHotkeys.put(id, !cur);
        s.sendMessage("§6§lControls: " + (!cur ? "§bHOTKEYS (F)" : "§eCOMMANDS"));
        return true;
    }
}

class InfoMenu implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player) s.sendMessage("§eElement: §f" + ElementSMP.playerElements.get(((Player) s).getUniqueId()));
        return true;
    }
}
