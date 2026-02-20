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
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static HashMap<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), djCD = new HashMap<>();
    private final List<String> baseElements = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("trust").setExecutor(new TrustHandler());
        getCommand("untrust").setExecutor(new TrustHandler());
        getCommand("elemental").setExecutor(new HelpCommand());
        getCommand("elementsmp").setExecutor(new AdminHandler());
        getCommand("give-reroll").setExecutor(new ItemHandler());
        getCommand("give-chaos").setExecutor(new ItemHandler());

        // Master HUD and Passive Task
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    applyPassives(p);
                    if (getElement(p).contains("wind") && !djCD.containsKey(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) {
                        p.setAllowFlight(true);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase(); }

    private void applyPassives(Player p) {
        String el = getElement(p);
        if (el.contains("lightning")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        if (el.contains("earth")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1, false, false));
        if (el.contains("blood")) p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
        if (el.contains("ice")) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false));
    }

    // --- UPDATED UI FORMAT: (Element) (AB1:Name(MM:SS)) (AB2:Name(MM:SS)) ---
  private void updateHUD(Player p) {
    String el = getElement(p).toUpperCase();
    UUID id = p.getUniqueId();
    long now = System.currentTimeMillis();

    String ab1Name = getAbilityName(el, 1);
    String ab2Name = getAbilityName(el, 2);

    String t1 = formatCooldown(cd1.getOrDefault(id, 0L) - now);
    String t2 = formatCooldown(cd2.getOrDefault(id, 0L) - now);

    // Matches your requested UI style perfectly
    String hud = String.format("§6§l%s §8| §bAb1: %s(%s) §8| §dAb2: %s(%s)", 
                  el, ab1Name, t1, ab2Name, t2);
    
    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
}

    private String formatCooldown(long ms) {
        if (ms <= 0) return "§aREADY";
        long sec = ms / 1000;
        return String.format("§c%02d:%02d", sec / 60, sec % 60);
    }

    private String getAbilityName(String el, int slot) {
        el = el.toLowerCase();
        if (slot == 1) {
            if (el.contains("nature")) return "Vine";
            if (el.contains("void")) return "Silence";
            if (el.contains("wind")) return "Dash";
            return "Burst";
        } else {
            if (el.contains("void") || el.contains("gravity")) return "Domain";
            return "Ultimate";
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!hotkeysEnabled.getOrDefault(e.getPlayer().getUniqueId(), true)) return;
        e.setCancelled(true);
        triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    // --- ANIMATED REROLL SYSTEM ---
    @EventHandler
    public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.NETHER_STAR) return;
        Player p = e.getPlayer();
        boolean isAdminChaos = item.getItemMeta().getDisplayName().contains("Chaos");
        item.setAmount(item.getAmount() - 1);

        new BukkitRunnable() {
            int ticks = 0;
            public void run() {
                if (ticks++ < 20) {
                    String roll = baseElements.get(new Random().nextInt(baseElements.size()));
                    p.sendTitle("§bRolling...", "§7> §f" + roll.toUpperCase() + " §7<", 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                } else {
                    String result;
                    if (isAdminChaos || new Random().nextDouble() < 0.001) {
                        result = baseElements.get(new Random().nextInt(10)) + "-" + baseElements.get(new Random().nextInt(10));
                        Bukkit.broadcastMessage("§5§lCHAOS! §d" + p.getName() + " §fhas achieved §5" + result.toUpperCase());
                        p.getWorld().strikeLightningEffect(p.getLocation());
                    } else {
                        result = baseElements.get(new Random().nextInt(10));
                    }
                    playerElements.put(p.getUniqueId(), result);
                    p.sendTitle("§6§l" + result.toUpperCase(), "§eElement Bound!", 10, 40, 10);
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    public void triggerAbility(Player p, int slot) {
        String el = getElement(p);
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        // NATURE VINE: EMERALD TRAIL & PULL
        if (el.contains("nature") && slot == 1) {
            RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 30);
            if (res != null && res.getHitBlock() != null) {
                Location hit = res.getHitPosition().toLocation(p.getWorld());
                Vector dir = hit.toVector().subtract(p.getEyeLocation().toVector()).normalize();
                for (double d = 0; d < p.getLocation().distance(hit); d += 0.5) {
                    p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getEyeLocation().add(dir.clone().multiply(d)), 2);
                }
                p.setVelocity(dir.multiply(2.0).setY(dir.getY() * 0.5 + 0.4));
                p.playSound(p.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1f, 1.2f);
                cd1.put(id, now + 5000);
            }
        }

        // VOID SILENCE (Ab1)
        if (el.contains("void") && slot == 1) {
            for (Entity e : p.getNearbyEntities(15, 15, 15)) {
                if (e instanceof Player t && !isTrusted(p, t)) {
                    cd1.put(t.getUniqueId(), now + 8000);
                    cd2.put(t.getUniqueId(), now + 8000);
                    t.sendMessage("§5§lSILENCED!");
                }
            }
            cd1.put(id, now + 25000);
        }

        // DOMAIN EXPANSION: DEEP BASS
        if (slot == 2 && (el.contains("void") || el.contains("gravity"))) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
            p.sendTitle("§d§lDOMAIN EXPANSION", "", 10, 40, 10);
            cd2.put(id, now + 60000);
        }
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            String el = getElement(p);
            if (el.contains("wind") || el.contains("gravity") || el.contains("void")) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onWindJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).contains("wind")) return;
        e.setCancelled(true); p.setAllowFlight(false);
        p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(1.0));
        djCD.put(p.getUniqueId(), now + 15000);
        new BukkitRunnable() { public void run() { djCD.remove(p.getUniqueId()); p.setAllowFlight(true); } }.runTaskLater(this, 300L);
    }

    private boolean isTrusted(Player owner, Player t) {
        return trustedPlayers.getOrDefault(owner.getUniqueId(), new HashSet<>()).contains(t.getUniqueId()) || owner.equals(t);
    }
}

// --- COMMAND CLASSES ---
class TrustHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || a.length == 0) return false;
        Player t = Bukkit.getPlayer(a[0]); if (t == null) return true;
        Set<UUID> set = ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (c.getName().equalsIgnoreCase("trust")) { set.add(t.getUniqueId()); p.sendMessage("§aTrusted " + t.getName()); }
        else { set.remove(t.getUniqueId()); p.sendMessage("§cUntrusted " + t.getName()); }
        return true;
    }
}
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { boolean b = !ElementSMP.hotkeysEnabled.getOrDefault(p.getUniqueId(), true); ElementSMP.hotkeysEnabled.put(p.getUniqueId(), b); p.sendMessage("§bHotkeys: " + (b ? "§aON" : "§cOFF")); } return true; } }
class HelpCommand implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { s.sendMessage("§6§lElement Help\n§fNature: Vine (Emerald)\n§5Void: Silence & Domain\n§fWind: Double Jump"); return true; } }
class AdminHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s.isOp() && a.length >= 3) { Player t = Bukkit.getPlayer(a[1]); if (t != null) ElementSMP.playerElements.put(t.getUniqueId(), a[2].toLowerCase()); } return true; } }
class ItemHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && s.isOp()) { ItemStack i = new ItemStack(Material.NETHER_STAR); ItemMeta m = i.getItemMeta(); m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll"); i.setItemMeta(m); p.getInventory().addItem(i); } return true; } }
