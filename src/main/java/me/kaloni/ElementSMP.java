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
    private final String[] elements = {"Fire", "Water", "Earth", "Air", "Ice", "Nature", "Lightning", "Shadow", "Light", "Magma", "Void", "Wind"};

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("elements").setExecutor(new AdminCommands(this));

        // PASSIVE TICKER (Every 5 seconds)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) applyPassives(p);
        }, 0L, 100L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        playerElements.putIfAbsent(p.getUniqueId(), elements[new Random().nextInt(elements.length)]);
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
        String icon = getIcon(e);
        long timeLeft = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();

        if (timeLeft > 0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§c§l" + icon + " COOLDOWN: " + (timeLeft / 1000) + "s"));
            return;
        }

        String name = (num == 1) ? getAbility1(e) : getAbility2(e);
        double dmg = (e.equals("Void") && num == 1) ? 12.0 : 6.0;

        p.sendMessage("§a§l" + icon + " Used: §f" + name);
        
        for (Entity target : p.getNearbyEntities(7, 7, 7)) {
            if (target instanceof LivingEntity && !target.equals(p)) {
                ((LivingEntity) target).damage(dmg, p);
                playEffects(target.getLocation(), e);
                break;
            }
        }

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a§l" + icon + " " + name.toUpperCase() + " ACTIVATED"));
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
        // FIX: Changed DAMAGE_RESISTANCE to RESISTANCE for modern compatibility
        if (e.equals("Void")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 0));
        if (e.equals("Fire")) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0));
    }

    public static String getIcon(String e) {
        switch (e) {
            case "Void": return "§5[?]§r";
            case "Fire": return "§c[!]§r";
            case "Lightning": return "§e[⚡]§r";
            default: return "§f[*]§r";
        }
    }
    
    private static String getAbility1(String e) { return e.equals("Void") ? "Singularity" : "Pulse Strike"; }
    private static String getAbility2(String e) { return e.equals("Void") ? "Abyssal Rift" : "Burst"; }
    public String[] getElementList() { return elements; }
}

class AdminCommands implements CommandExecutor {
    private final ElementSMP plugin;
    public AdminCommands(ElementSMP plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.isOp()) {
            s.sendMessage("§cOnly operators can use admin element commands!");
            return true;
        }

        if (a.length >= 2 && a[0].equalsIgnoreCase("set")) {
            Player target = Bukkit.getPlayer(a[1]);
            if (target == null) { s.sendMessage("§cPlayer not found."); return true; }
            String newEl = (a.length > 2) ? a[2] : "Void";
            ElementSMP.playerElements.put(target.getUniqueId(), newEl);
            s.sendMessage("§aSet " + target.getName() + " to " + newEl);
            return true;
        }
        
        if (a.length >= 2 &&
