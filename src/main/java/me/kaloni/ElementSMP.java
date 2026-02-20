package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
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

    // Global Maps
    public static final HashMap<UUID, String> playerElements = new HashMap<>();
    public static final HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    public static final HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static final HashMap<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), djCD = new HashMap<>();
    
    // Domain Tracking
    public static final HashMap<UUID, Location> activeDomains = new HashMap<>();
    public static final HashMap<Location, List<Block>> domainBlocks = new HashMap<>();

    private final List<String> elements = Arrays.asList(
            "Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity"
    );

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();

        // Master HUD and Passive Task
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

    // --- HUD SYSTEM ---
    private void updateHUD(Player p) {
        String el = getElement(p).toUpperCase();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        String ab1Name = getAbilityName(el, 1);
        String ab2Name = getAbilityName(el, 2);
        String t1 = formatCD(cd1.getOrDefault(id, 0L) - now);
        String t2 = formatCD(cd2.getOrDefault(id, 0L) - now);

        // Required Format: (Element) (AB1:Name(00:00)) (AB2:Name(00:00))
        String hud = String.format("§6§l(%s) §b(AB1:%s(%s)) §d(AB2:%s(%s))", el, ab1Name, t1, ab2Name, t2);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    private String formatCD(long ms) {
        if (ms <= 0) return "§aREADY";
        return String.format("%02d:%02d", (ms/1000)/60, (ms/1000)%60);
    }

    private String getAbilityName(String el, int slot) {
        el = el.toLowerCase();
        if (slot == 1) {
            if (el.contains("nature")) return "Vine";
            if (el.contains("void")) return "Silence";
            return "Dash";
        }
        return (el.contains("void") || el.contains("gravity")) ? "Domain" : "Ultimate";
    }

    // --- COMBAT GUARD ---
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        for (Location loc : activeDomains.values()) {
            if (p.getLocation().distance(loc) < 15) {
                p.setHealth(0); // Kill combat loggers inside domains
                Bukkit.broadcastMessage("§c" + p.getName() + " died for logging out in a Domain!");
                break;
            }
        }
    }

    // --- ABILITIES ---
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

        if (slot == 1) {
            // NATURE: EMERALD VINE
            if (el.contains("nature")) {
                RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 30);
                if (res != null) {
                    Location hit = res.getHitPosition().toLocation(p.getWorld());
                    BlockData emerald = Material.EMERALD_BLOCK.createBlockData(); // 1.21 Fix
                    Vector v = hit.toVector().subtract(p.getEyeLocation().toVector()).normalize();
                    for (double d = 0; d < p.getLocation().distance(hit); d += 0.5) {
                        p.getWorld().spawnParticle(Particle.BLOCK, p.getEyeLocation().add(v.clone().multiply(d)), 1, emerald);
                    }
                    p.setVelocity(v.multiply(1.8).setY(v.getY() * 0.4 + 0.4));
                    p.playSound(p.getLocation(), Sound.ENTITY_LEASH_KNOT_PLACE, 1f, 1.2f);
                    cd1.put(id, now + 5000);
                }
            }
        } 
        else if (slot == 2) {
            // DOMAIN EXPANSION: CAGE & SHATTER
            if (el.contains("void") || el.contains("gravity")) {
                Location center = p.getLocation();
                activeDomains.put(id, center);
                p.sendTitle("§d§lDOMAIN EXPANSION", "§7Infinite Darkness", 10, 50, 10);
                p.getWorld().playSound(center, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
                
                List<Block> cage = new ArrayList<>();
                Material glassType = el.contains("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
                
                for (int x = -7; x <= 7; x++) {
                    for (int y = -7; y <= 7; y++) {
                        for (int z = -7; z <= 7; z++) {
                            double d = Math.sqrt(x*x + y*y + z*z);
                            if (d > 6.5 && d < 7.5) {
                                Block b = center.clone().add(x, y, z).getBlock();
                                if (b.getType().isAir()) {
                                    b.setType(glassType);
                                    cage.add(b);
                                }
                            }
                        }
                    }
                }
                domainBlocks.put(center, cage);

                // Domain End: Cinematic Shatter
                new BukkitRunnable() {
                    public void run() {
                        activeDomains.remove(id);
                        for (Block b : cage) {
                            if (b.getType() == glassType) {
                                p.getWorld().spawnParticle(Particle.BLOCK, b.getLocation(), 5, glassType.createBlockData());
                                b.setType(Material.AIR);
                            }
                        }
                        p.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
                        domainBlocks.remove(center);
                    }
                }.runTaskLater(this, 300L); // 15 Seconds
                cd2.put(id, now + 60000);
            }
        }
    }

    private void handlePassives(Player p) {
        String el = getElement(p);
        if (el.contains("lightning")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        if (el.contains("earth")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 1, false, false));
        if (el.contains("wind") && p.getGameMode() == GameMode.SURVIVAL && !djCD.containsKey(p.getUniqueId())) {
            p.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.NETHER_STAR) return;
        Player p = e.getPlayer();
        boolean chaos = item.getItemMeta().getDisplayName().contains("Chaos");
        item.setAmount(item.getAmount() - 1);

        new BukkitRunnable() {
            int t = 0;
            public void run() {
                if (t++ < 20) {
                    p.sendTitle("§bRolling...", "§f" + elements.get(new Random().nextInt(10)).toUpperCase(), 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f + (t*0.05f));
                } else {
                    String res = chaos ? elements.get(new Random().nextInt(10)) + "-" + elements.get(new Random().nextInt(10)) : elements.get(new Random().nextInt(10));
                    playerElements.put(p.getUniqueId(), res);
                    p.sendTitle("§6" + res.toUpperCase(), "§eElement Bound!", 10, 40, 10);
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private boolean isTrusted(Player p, Entity e) {
        if (!(e instanceof Player target)) return false;
        return trustedPlayers.getOrDefault(p.getUniqueId(), new HashSet<>()).contains(target.getUniqueId()) || p.equals(target);
    }
}

// Support Commands
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
class AdminHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s.isOp() && a.length >= 3) { Player t = Bukkit.getPlayer(a[1]); if (t != null) ElementSMP.playerElements.put(t.getUniqueId(), a[2]); } return true; } }
class ItemHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p && s.isOp()) { ItemStack i = new ItemStack(Material.NETHER_STAR); ItemMeta m = i.getItemMeta(); m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll"); i.setItemMeta(m); p.getInventory().addItem(i); } return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { boolean b = !ElementSMP.hotkeysEnabled.getOrDefault(p.getUniqueId(), true); ElementSMP.hotkeysEnabled.put(p.getUniqueId(), b); p.sendMessage("§bHotkeys: " + (b ? "§aON" : "§cOFF")); } return true; } }
class HelpCommand implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { s.sendMessage("§6§lElementSMP\n§fSwap -> Ab1\n§fShift+Swap -> Ab2"); return true; } }
