package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
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
import org.bukkit.util.Vector;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static final HashMap<UUID, String> playerElements = new HashMap<>();
    public static final HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    public static final HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static final HashMap<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), djCD = new HashMap<>();
    
    private final List<String> baseElements = Arrays.asList(
            "Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity"
    );

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    handlePassives(p);
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    private void registerCommands() {
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("trust").setExecutor(new TrustHandler());
        getCommand("untrust").setExecutor(new TrustHandler());
        getCommand("elemental").setExecutor(new HelpCommand());
        getCommand("elementsmp").setExecutor(new AdminHandler());
        getCommand("give-reroll").setExecutor(new ItemHandler());
        getCommand("give-chaos").setExecutor(new ItemHandler());
    }

    public String getElement(Player p) {
        return playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase();
    }

    private void updateHUD(Player p) {
        String el = getElement(p).toUpperCase();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        String ab1 = getAbilityName(el, 1);
        String ab2 = getAbilityName(el, 2);
        String t1 = formatCooldown(cd1.getOrDefault(id, 0L) - now);
        String t2 = formatCooldown(cd2.getOrDefault(id, 0L) - now);

        String hud = String.format("§6§l(%s) §b(AB1:%s(%s)) §d(AB2:%s(%s))", el, ab1, t1, ab2, t2);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    private String formatCooldown(long ms) {
        if (ms <= 0) return "§aREADY";
        return String.format("%02d:%02d", (ms/1000)/60, (ms/1000)%60);
    }

    private String getAbilityName(String el, int slot) {
        el = el.toLowerCase();
        if (slot == 1) {
            if (el.contains("fire")) return "Burst";
            if (el.contains("water")) return "Wave";
            if (el.contains("nature")) return "Vine";
            if (el.contains("void")) return "Silence";
            if (el.contains("lightning")) return "Bolt";
            if (el.contains("gravity")) return "Lift";
            if (el.contains("ice")) return "Freeze";
            return "Dash";
        } else {
            if (el.contains("void") || el.contains("gravity")) return "Domain";
            if (el.contains("blood")) return "Rage";
            if (el.contains("earth")) return "Shield";
            return "Ultimate";
        }
    }

    private void handlePassives(Player p) {
        String el = getElement(p);
        if (el.contains("lightning")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, false, false));
        if (el.contains("earth")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, false, false));
        if (el.contains("blood")) p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 0, false, false));
        if (el.contains("wind") && !djCD.containsKey(p.getUniqueId())) p.setAllowFlight(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!hotkeysEnabled.getOrDefault(e.getPlayer().getUniqueId(), true)) return;
        e.setCancelled(true);
        triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    public void triggerAbility(Player p, int slot) {
        String el = getElement(p);
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        // --- ABILITY 1 LOGIC ---
        if (slot == 1) {
            if (el.contains("nature")) {
                RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
                if (res != null) {
                    Location hit = res.getHitPosition().toLocation(p.getWorld());
                    Vector dir = hit.toVector().subtract(p.getEyeLocation().toVector()).normalize();
                    p.getWorld().spawnParticle(Particle.BLOCK, hit, 30, Material.EMERALD_BLOCK.createBlockData());
                    p.setVelocity(dir.multiply(2.0).setY(dir.getY() * 0.5 + 0.5));
                    p.playSound(p.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1f, 1.2f);
                }
            } else if (el.contains("fire")) {
                p.setVelocity(p.getLocation().getDirection().multiply(1.5));
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
                p.getNearbyEntities(5, 5, 5).forEach(en -> { if (en instanceof LivingEntity le && !isTrusted(p, le)) le.setFireTicks(100); });
            } else if (el.contains("ice")) {
                p.getNearbyEntities(7, 7, 7).forEach(en -> { if (en instanceof LivingEntity le && !isTrusted(p, le)) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2)); });
                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 100, 3, 1, 3, 0.1);
            }
            cd1.put(id, now + 5000);
        }

        // --- ABILITY 2 LOGIC (DOMAIN / ULTS) ---
        if (slot == 2) {
            if (el.contains("void") || el.contains("gravity")) {
                p.sendTitle("§d§lDOMAIN EXPANSION", "§7Infinite Information", 10, 60, 10);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 1));
            } else if (el.contains("earth")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 3));
                p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 200, 2, 0, 2, Material.OBSIDIAN.createBlockData());
            }
            cd2.put(id, now + 60000);
        }
    }

    @EventHandler
    public void onLifesteal(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && getElement(p).contains("blood")) {
            p.setHealth(Math.min(p.getMaxHealth(), p.getHealth() + 1));
            p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, e.getEntity().getLocation(), 5);
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
    public void onReroll(PlayerInteractEvent e) {
        ItemStack i = e.getItem();
        if (i == null || i.getType() != Material.NETHER_STAR) return;
        Player p = e.getPlayer();
        boolean chaos = i.getItemMeta().getDisplayName().contains("Chaos");
        i.setAmount(i.getAmount() - 1);
        new BukkitRunnable() {
            int t = 0;
            public void run() {
                if (t++ < 20) {
                    p.sendTitle("§bRolling...", "§f" + baseElements.get(new Random().nextInt(10)).toUpperCase(), 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f + (t*0.05f));
                } else {
                    String res = chaos ? baseElements.get(new Random().nextInt(10)) + "-" + baseElements.get(new Random().nextInt(10)) : baseElements.get(new Random().nextInt(10));
                    playerElements.put(p.getUniqueId(), res);
                    p.sendTitle("§6" + res.toUpperCase(), "§eElement Bound!", 10, 40, 10);
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private boolean isTrusted(Player p, Entity e) {
        if (!(e instanceof Player target)) return false;
        return trustedPlayers.getOrDefault(p.getUniqueId(), new HashSet<>()).contains(target.getUniqueId());
    }
}

// Support Classes (Trust, Controls, Admin)
class TrustHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || a.length == 0) return false;
        Player t = Bukkit.getPlayer(a[0]); if (t == null) return true;
        Set<UUID> set = ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (c.getName().equalsIgnoreCase("trust")) set.add(t.getUniqueId()); else set.remove(t.getUniqueId());
        p.sendMessage("§7Trust updated for " + t.getName());
        return true;
    }
}
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { boolean b = !ElementSMP.hotkeysEnabled.getOrDefault(p.getUniqueId(), true); ElementSMP.hotkeysEnabled.put(p.getUniqueId(), b); p.sendMessage("§bHotkeys: " + (b ? "§aON" : "§cOFF")); } return true; } }
class HelpCommand implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { s.sendMessage("§6§lElementSMP\n§fUse Swap (F) for Ability 1\n§fSneak + Swap for Ability 2"); return true; } }
class AdminHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s.isOp() && a.length >= 3) { Player t = Bukkit.getPlayer(a[1]); if (t != null) ElementSMP.playerElements.put(t.getUniqueId(), a[2]); } return true; } }
class ItemHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && s.isOp()) { ItemStack i = new ItemStack(Material.NETHER_STAR); ItemMeta m = i.getItemMeta(); m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll"); i.setItemMeta(m); p.getInventory().addItem(i); } return true; } }
