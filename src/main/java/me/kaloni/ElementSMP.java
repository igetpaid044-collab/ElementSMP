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

import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static HashMap<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), djCD = new HashMap<>();
    private static final HashMap<UUID, Location> activeDomains = new HashMap<>();
    
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

        // Passive/HUD Task
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    checkPassives(p);
                    if (getElement(p).contains("wind") && !djCD.containsKey(p.getUniqueId()) && p.getGameMode() == GameMode.SURVIVAL) p.setAllowFlight(true);
                }
            }
        }.runTaskTimer(this, 0L, 10L);
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase(); }

    private void checkPassives(Player p) {
        String el = getElement(p);
        if (el.contains("lightning")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, false, false));
        if (el.contains("earth")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, false, false));
        if (el.contains("blood")) p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 0, false, false));
        if (el.contains("ice")) p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, false, false));
    }

    private void updateHUD(Player p) {
        String el = getElement(p).toUpperCase();
        long now = System.currentTimeMillis();
        String s1 = (cd1.getOrDefault(p.getUniqueId(), 0L) > now) ? "§c" + (cd1.get(p.getUniqueId()) - now)/1000 + "s" : "§aREADY";
        String s2 = (cd2.getOrDefault(p.getUniqueId(), 0L) > now) ? "§c" + (cd2.get(p.getUniqueId()) - now)/1000 + "s" : "§aREADY";
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§6" + el + " §8| §bAb1: " + s1 + " §8| §dAb2: " + s2));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!hotkeysEnabled.getOrDefault(p.getUniqueId(), true)) return;
        e.setCancelled(true);
        triggerAbility(p, p.isSneaking() ? 2 : 1);
    }

    // --- REROLL ANIMATION ---
    @EventHandler
    public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.NETHER_STAR) return;
        Player p = e.getPlayer();
        boolean opChaos = item.getItemMeta().getDisplayName().contains("Chaos");
        item.setAmount(item.getAmount() - 1);

        new BukkitRunnable() {
            int ticks = 0;
            public void run() {
                if (ticks++ < 20) {
                    String randomEl = baseElements.get(new Random().nextInt(baseElements.size()));
                    p.sendTitle("§bRolling...", "§7> §f" + randomEl.toUpperCase() + " §7<", 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                } else {
                    String finalEl;
                    boolean isChaos = opChaos || (new Random().nextDouble() < 0.001);
                    if (isChaos) {
                        finalEl = baseElements.get(new Random().nextInt(10)) + "-" + baseElements.get(new Random().nextInt(10));
                        Bukkit.broadcastMessage("§5§lCHAOS! §d" + p.getName() + " §fhas achieved §5" + finalEl.toUpperCase());
                        p.getWorld().strikeLightningEffect(p.getLocation());
                    } else {
                        finalEl = baseElements.get(new Random().nextInt(10));
                    }
                    playerElements.put(p.getUniqueId(), finalEl);
                    p.sendTitle("§6§l" + finalEl.toUpperCase(), "§eElement Bound!", 10, 40, 10);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // --- ABILITIES ---
    public void triggerAbility(Player p, int slot) {
        String el = getElement(p);
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        // NATURE: GRAPPLING VINE (Ab1)
        if (el.contains("nature") && slot == 1) {
            RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
            if (res != null && res.getHitBlock() != null) {
                Location target = res.getHitPosition().toLocation(p.getWorld());
                Vector dir = target.toVector().subtract(p.getLocation().toVector()).normalize();
                p.setVelocity(dir.multiply(1.8).setY(dir.getY() + 0.5));
                // Emerald Particles
                for (double i = 0; i < p.getLocation().distance(target); i += 0.5) {
                    p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(dir.clone().multiply(i)), 2);
                }
                p.playSound(p.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1f, 1.2f);
                cd1.put(id, now + 6000);
            }
        }

        // VOID: SILENCE (Ab1)
        if (el.contains("void") && slot == 1) {
            for (Entity e : p.getNearbyEntities(15, 15, 15)) {
                if (e instanceof Player t && !isTrusted(p, t)) {
                    cd1.put(t.getUniqueId(), now + 8000);
                    cd2.put(t.getUniqueId(), now + 8000);
                    t.sendMessage("§5§lSILENCED! §7Your abilities are temporarily disabled.");
                }
            }
            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation(), 100, 2, 2, 2);
            cd1.put(id, now + 25000);
        }

        // DOMAIN EXPANSIONS (Ab2)
        if (slot == 2 && (el.contains("void") || el.contains("gravity"))) {
            activeDomains.put(id, p.getLocation());
            p.sendMessage("§d§lDOMAIN EXPANSION!");
            new BukkitRunnable() { public void run() { activeDomains.remove(id); } }.runTaskLater(this, 400L);
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
        djCD.put(p.getUniqueId(), System.currentTimeMillis() + 15000);
        new BukkitRunnable() { public void run() { djCD.remove(p.getUniqueId()); p.setAllowFlight(true); } }.runTaskLater(this, 300L);
    }

    private boolean isTrusted(Player owner, Player target) {
        return trustedPlayers.getOrDefault(owner.getUniqueId(), new HashSet<>()).contains(target.getUniqueId()) || owner.equals(target);
    }
}

// --- COMMAND HANDLERS ---
class TrustHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || a.length == 0) return false;
        Player t = Bukkit.getPlayer(a[0]); if (t == null) return false;
        Set<UUID> set = ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (c.getName().equalsIgnoreCase("trust")) { set.add(t.getUniqueId()); p.sendMessage("§aTrusted " + t.getName()); }
        else { set.remove(t.getUniqueId()); p.sendMessage("§cUntrusted " + t.getName()); }
        return true;
    }
}

class HelpCommand implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        s.sendMessage("§6§lElement Help\n§bNature: §fGrappling Vine (Emerald Trail)\n§5Void: §fSilence Aura & Domain Expansion\n§fWind: §fDouble Jump & Fall Immunity");
        return true;
    }
}

class AdminHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s.isOp() && a.length >= 3 && a[0].equalsIgnoreCase("set")) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t != null) ElementSMP.playerElements.put(t.getUniqueId(), a[2].toLowerCase());
        }
        return true;
    }
}

class ControlToggle implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) {
            boolean b = !ElementSMP.hotkeysEnabled.getOrDefault(p.getUniqueId(), true);
            ElementSMP.hotkeysEnabled.put(p.getUniqueId(), b);
            p.sendMessage("§bHotkeys: " + (b ? "§aON" : "§cOFF"));
        }
        return true;
    }
}

class ItemHandler implements CommandExecutor {
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p && s.isOp()) {
            ItemStack i = new ItemStack(Material.NETHER_STAR); ItemMeta m = i.getItemMeta();
            m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll");
            i.setItemMeta(m); p.getInventory().addItem(i);
        }
        return true;
    }
}
