package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    private static ElementSMP instance;
    public static ElementSMP getInstance() { return instance; }

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, String> secondaryElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>(); // Trust System
    public static HashMap<UUID, Long> cd1 = new HashMap<>();
    public static HashMap<UUID, Long> cd2 = new HashMap<>();
    
    private final List<String> elementList = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity");

    @Override
    public void onEnable() {
        instance = this;
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new AdminItemCommand());
        getCommand("give-chaos").setExecutor(new AdminItemCommand());
        getCommand("elementsmp").setExecutor(new AdminElementHandler());
        
        // Trust Commands
        TrustCommand trustCmd = new TrustCommand();
        getCommand("trust").setExecutor(trustCmd);
        getCommand("untrust").setExecutor(trustCmd);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateActionBarHUD(p);
                    applyPassives(p);
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    // --- TRUST SYSTEM CHECK ---
    public static boolean isTrusted(Player owner, Entity target) {
        if (!(target instanceof Player)) return false;
        return trustedPlayers.getOrDefault(owner.getUniqueId(), new HashSet<>()).contains(target.getUniqueId());
    }

    // --- INFINITE VOID LOGIC ---
    private static boolean performInfiniteVoid(Player p) {
        p.sendMessage("§5§lDomain Expansion: §0§lINFINITE VOID");
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.5f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);

        Location center = p.getLocation();
        int radius = 25;

        // 1. Engulf in Sculk (Temporary Visual transformation)
        List<Block> changedBlocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 5; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = center.clone().add(x, y, z);
                    if (loc.distance(center) <= radius && loc.getBlock().getType().isSolid()) {
                        Block b = loc.getBlock();
                        if (b.getType() != Material.SCULK) {
                            p.sendBlockChange(loc, Material.SCULK.createBlockData()); // Client-side only to prevent griefing
                        }
                    }
                }
            }
        }

        // 2. Spawn Void Sentinels
        spawnVoidGuard(p, center, EntityType.WARDEN, "§5§lVoid Sentinel");
        for (int i = 0; i < 3; i++) spawnVoidGuard(p, center, EntityType.ENDERMAN, "§dShadow Guard");

        // 3. Effect Enemies
        for (Entity e : p.getNearbyEntities(radius, 10, radius)) {
            if (e instanceof Player target && !isTrusted(p, target) && target != p) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 200, 1));
                target.sendMessage("§5§oCaught in the Void...");
            }
        }
        return true;
    }

    private static void spawnVoidGuard(Player owner, Location loc, EntityType type, String name) {
        LivingEntity guard = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        guard.setCustomName(name);
        guard.setCustomNameVisible(true);
        guard.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
        
        // Nerf Damage to ~1 Heart
        guard.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(2.0);

        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks > 400 || !guard.isValid()) { guard.remove(); this.cancel(); return; }
                // Auto-Aggro non-trusted players
                for (Entity e : guard.getNearbyEntities(15, 15, 15)) {
                    if (e instanceof Player target && !isTrusted(owner, target) && target != owner) {
                        if (guard instanceof Mob mob) mob.setTarget(target);
                    }
                }
                ticks += 20;
            }
        }.runTaskTimer(getInstance(), 0L, 20L);
    }

    // --- UPDATED ABILITY TRIGGER ---
    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toLowerCase();
        HashMap<UUID, Long> cdMap = (num == 1) ? cd1 : cd2;
        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) return;

        boolean success = false;
        if (num == 1) {
            success = switch (el) {
                case "nature" -> performNatureGrapple(p);
                case "void" -> performVoidWarp(p);
                case "fire" -> performFireBurst(p);
                default -> false;
            };
        } else {
            success = switch (el) {
                case "void" -> performInfiniteVoid(p);
                case "ice" -> performAbsoluteZero(p);
                default -> false;
            };
        }

        if (success) cdMap.put(id, System.currentTimeMillis() + (num == 1 ? 15000 : 60000));
    }

    // --- REUSED ABILITIES (With Trust Checks) ---
    private static boolean performAbsoluteZero(Player p) {
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
        for (Entity e : p.getNearbyEntities(10, 5, 10)) {
            if (e instanceof Player target && !isTrusted(p, target) && target != p) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 255));
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, 200));
                target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, Material.ICE.createBlockData());
            }
        }
        return true;
    }

    private static boolean performNatureGrapple(Player p) {
        RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
        if (res == null || res.getHitBlock() == null) return false;
        Location hit = res.getHitPosition().toLocation(p.getWorld());
        Vector dir = hit.toVector().subtract(p.getEyeLocation().toVector()).normalize();
        p.setVelocity(dir.multiply(2.1).setY(0.5));
        return true;
    }

    private static boolean performVoidWarp(Player p) {
        Location loc = p.getEyeLocation().add(p.getLocation().getDirection().multiply(8));
        p.teleport(loc);
        return true;
    }

    private static boolean performFireBurst(Player p) {
        p.setVelocity(new Vector(0, 1.2, 0));
        for (Entity e : p.getNearbyEntities(5, 5, 5)) { 
            if (e instanceof LivingEntity le && !isTrusted(p, e) && e != p) le.setFireTicks(100); 
        }
        return true;
    }

    // --- CORE HUD & PASSIVES (RETAINED) ---
    private void updateActionBarHUD(Player p) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toUpperCase();
        long c1 = (cd1.getOrDefault(id, 0L) - System.currentTimeMillis()) / 1000;
        long c2 = (cd2.getOrDefault(id, 0L) - System.currentTimeMillis()) / 1000;
        String msg = String.format("§b§l%s §8| §eA1: %s §8| §6A2: %s", el, (c1<=0?"§aREADY":"§c"+c1+"s"), (c2<=0?"§aREADY":"§c"+c2+"s"));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    private void applyPassives(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase();
        if (el.equals("void")) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0, false, false));
    }

    @EventHandler public void onReroll(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.NETHER_STAR) {
            e.setCancelled(true);
            startRerollAnimation(e.getPlayer(), ChatColor.stripColor(e.getItem().getItemMeta().getDisplayName()).toLowerCase().contains("chaos"));
            e.getItem().setAmount(e.getItem().getAmount() - 1);
        }
    }

    public void startRerollAnimation(Player p, boolean isChaos) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks < 25) {
                    p.sendTitle("§7§lREROLLING", "§f" + elementList.get(new Random().nextInt(elementList.size())), 0, 5, 0);
                } else {
                    String el1 = elementList.get(new Random().nextInt(elementList.size()));
                    playerElements.put(p.getUniqueId(), el1);
                    p.sendTitle("§a§l" + el1.toUpperCase(), "§7Locked!", 10, 40, 10);
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) {
        if (useHotkeys.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }
}

// --- NEW TRUST COMMANDS ---
class TrustCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || a.length == 0) return false;
        Player target = Bukkit.getPlayer(a[0]);
        if (target == null) return false;

        Set<UUID> trusted = ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (c.getName().equalsIgnoreCase("trust")) {
            trusted.add(target.getUniqueId());
            p.sendMessage("§aYou now trust " + target.getName());
        } else {
            trusted.remove(target.getUniqueId());
            p.sendMessage("§cYou no longer trust " + target.getName());
        }
        return true;
    }
}

// --- ADMIN & CONTROLS ---
class AdminItemCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || !s.isOp()) return true;
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = star.getItemMeta();
        m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll");
        star.setItemMeta(m); p.getInventory().addItem(star); return true;
    }
}
class AdminElementHandler implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (s.isOp() && args.length >= 2) {
            Player t = Bukkit.getPlayer(args[1]);
            if (t != null) { ElementSMP.playerElements.put(t.getUniqueId(), args[2]); s.sendMessage("§aDone."); }
        }
        return true;
    }
}
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled."); } return true; } }
