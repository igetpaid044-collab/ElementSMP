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
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    private static final HashMap<UUID, Location> activeDomains = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("elemental").setExecutor(new HelpCommand());
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
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase(); }

    // --- CONTROLS: F KEY LOGIC ---
    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!hotkeysEnabled.getOrDefault(p.getUniqueId(), true)) return;
        
        e.setCancelled(true); // Prevent item swapping
        if (p.isSneaking()) {
            triggerAbility(p, 2);
        } else {
            triggerAbility(p, 1);
        }
    }

    // --- HUD SYSTEM ---
    private void updateHUD(Player p) {
        String el = getElement(p).toUpperCase();
        String controls = hotkeysEnabled.getOrDefault(p.getUniqueId(), true) ? "§aF / Shift+F" : "§c/ability";
        
        String ability1 = "Dash";
        String ability2 = "Utility";
        
        if (el.contains("fire")) { ability1 = "Flame Dash"; ability2 = "Inferno AOE"; }
        if (el.contains("void")) { ability1 = "Rift Step"; ability2 = "Domain"; }
        if (el.contains("fusion")) { ability1 = "FUSION POWER"; ability2 = "Core"; }

        String msg = "§6§l" + el + " §8| §fAb1: §b" + ability1 + " §8| §fAb2: §d" + ability2 + " §8| §7[" + controls + "]";
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    // --- ABILITY LOGIC ---
    public static void triggerAbility(Player p, int slot) {
        ElementSMP plugin = (ElementSMP) Bukkit.getPluginManager().getPlugin("ElementSMP");
        String el = plugin.getElement(p);

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2f);

        if (el.contains("fire")) {
            if (slot == 1) { p.setVelocity(p.getLocation().getDirection().multiply(1.8)); p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 30, 0.2, 0.2, 0.2, 0.1); }
            else { for (Entity e : p.getNearbyEntities(5, 5, 5)) if (e instanceof LivingEntity t) t.setFireTicks(100); }
        } else if (el.contains("void")) {
            if (slot == 1) { p.teleport(p.getTargetBlockExact(12).getLocation().add(0, 1, 0)); p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 50); }
            else { plugin.startDomain(p); }
        } else {
            p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(0.4));
        }
    }

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
}

// --- COMMAND HANDLERS ---
class HelpCommand implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0 || !a[0].equalsIgnoreCase("help")) {
            s.sendMessage("§b§lElementSMP Help\n§f/el help <element> §7- View Lore & Skills\n§f/controls §7- Toggle Hotkeys");
            return true;
        }
        String type = a[1].toLowerCase();
        s.sendMessage("§8§m----------------------------");
        if (type.equals("fire")) {
            s.sendMessage("§6§lFIRE ELEMENT §7(The Burning Passion)");
            s.sendMessage("§eLore: §fBorn from the core of the Nether, Fire users channel pure energy.");
            s.sendMessage("§bAbility 1: §fFlame Dash - Explode forward.");
            s.sendMessage("§dAbility 2: §fInferno - Burn all nearby enemies.");
        } else if (type.equals("void")) {
            s.sendMessage("§5§lVOID ELEMENT §7(The Infinite Dark)");
            s.sendMessage("§eLore: §fWanderers of the End who mastered the art of space-time.");
            s.sendMessage("§bAbility 1: §fRift Step - Instant short-range teleport.");
            s.sendMessage("§dAbility 2: §fDomain - Trap enemies in a blinding void.");
        } else {
            s.sendMessage("§cElement lore not found. Try Fire or Void!");
        }
        s.sendMessage("§8§m----------------------------");
        return true;
    }
}

class ControlToggle implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) {
            boolean current = ElementSMP.hotkeysEnabled.getOrDefault(p.getUniqueId(), true);
            ElementSMP.hotkeysEnabled.put(p.getUniqueId(), !current);
            p.sendMessage("§bHotkeys (F/Shift+F) are now: " + (!current ? "§aENABLED" : "§cDISABLED"));
        }
        return true;
    }
}

// Keep the AdminHandler, ItemHandler, AbilityHandler, and TrustHandler from previous steps...
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2") ? 2 : 1)); return true; } }
class AdminHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s.isOp() && a.length >= 2) { Player t = Bukkit.getPlayer(a[0]); if (t != null) ElementSMP.playerElements.put(t.getUniqueId(), a[1]); } return true; } }
class ItemHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && s.isOp()) { ItemStack i = new ItemStack(Material.NETHER_STAR); ItemMeta m = i.getItemMeta(); m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll"); i.setItemMeta(m); p.getInventory().addItem(i); } return true; } }
class TrustHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && a.length > 0) { Player t = Bukkit.getPlayer(a[0]); if (t != null) ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(t.getUniqueId()); p.sendMessage("§aTrusted!"); } return true; } }
