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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener {

    private static ElementSMP instance;
    public static ElementSMP getInstance() { return instance; }

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    public static HashMap<UUID, Long> cd1 = new HashMap<>();
    public static HashMap<UUID, Long> cd2 = new HashMap<>();
    
    private final List<String> elementList = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Gravity");

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new AdminItemCommand());
        getCommand("give-chaos").setExecutor(new AdminItemCommand());
        getCommand("elementsmp").setExecutor(new AdminElementHandler());
        
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
        }.runTaskTimer(this, 0L, 10L);
    }

    public static boolean isTrusted(Player owner, Entity target) {
        if (!(target instanceof Player)) return false;
        return trustedPlayers.getOrDefault(owner.getUniqueId(), new HashSet<>()).contains(target.getUniqueId());
    }

    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toLowerCase();
        HashMap<UUID, Long> cdMap = (num == 1) ? cd1 : cd2;

        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            p.sendMessage("§cWait " + (cdMap.get(id) - System.currentTimeMillis())/1000 + "s");
            return;
        }

        boolean success = switch (el) {
            case "void" -> (num == 1) ? performVoidWarp(p) : performInfiniteVoid(p);
            case "ice" -> (num == 2) ? performAbsoluteZero(p) : false;
            case "nature" -> (num == 1) ? performNatureGrapple(p) : false;
            default -> false;
        };

        if (success) {
            long cooldown = getInstance().getConfig().getLong("cooldowns.a" + num, (num == 1 ? 12 : 60)) * 1000;
            cdMap.put(id, System.currentTimeMillis() + cooldown);
        }
    }

    private static boolean performInfiniteVoid(Player p) {
        int radius = getInstance().getConfig().getInt("abilities.void.domain-radius", 25);
        p.sendMessage("§5§lDomain Expansion: §0§lINFINITE VOID");
        Location center = p.getLocation();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                Location loc = center.clone().add(x, -1, z);
                if (loc.distance(center) <= radius) p.sendBlockChange(loc, Material.SCULK.createBlockData());
            }
        }

        spawnVoidGuard(p, center, EntityType.WARDEN, "§5§lVoid Sentinel");
        for (Entity e : p.getNearbyEntities(radius, 10, radius)) {
            if (e instanceof Player target && !isTrusted(p, target) && target != p) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 200, 0));
            }
        }
        return true;
    }

    private static void spawnVoidGuard(Player owner, Location loc, EntityType type, String name) {
        LivingEntity guard = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        guard.setCustomName(name);
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks > 400 || !guard.isValid()) { guard.remove(); this.cancel(); return; }
                for (Entity e : guard.getNearbyEntities(15, 15, 15)) {
                    if (e instanceof Player target && !isTrusted(owner, target) && target != owner) {
                        if (guard instanceof Mob mob) mob.setTarget(target);
                    }
                }
                ticks += 20;
            }
        }.runTaskTimer(getInstance(), 0L, 20L);
    }

    private static boolean performAbsoluteZero(Player p) {
        int r = getInstance().getConfig().getInt("abilities.ice.freeze-radius", 10);
        for (Entity e : p.getNearbyEntities(r, 5, r)) {
            if (e instanceof Player target && !isTrusted(p, target) && target != p) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 255));
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 200));
            }
        }
        return true;
    }

    private static boolean performNatureGrapple(Player p) {
        RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
        if (res == null || res.getHitBlock() == null) return false;
        p.setVelocity(res.getHitPosition().toLocation(p.getWorld()).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2.1));
        return true;
    }

    private static boolean performVoidWarp(Player p) {
        p.teleport(p.getEyeLocation().add(p.getLocation().getDirection().multiply(8)));
        return true;
    }

    private void updateActionBarHUD(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind").toUpperCase();
        long c1 = (cd1.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis()) / 1000;
        long c2 = (cd2.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis()) / 1000;
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
            playerElements.put(e.getPlayer().getUniqueId(), elementList.get(new Random().nextInt(elementList.size())));
            e.getPlayer().sendMessage("§aElement Rerolled!");
            e.getItem().setAmount(e.getItem().getAmount() - 1);
        }
    }

    @EventHandler public void onSwap(PlayerSwapHandItemsEvent e) {
        if (useHotkeys.getOrDefault(e.getPlayer().getUniqueId(), false)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }
}

// --- COMMAND HANDLERS ---
class AdminElementHandler implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!s.isOp()) return true;
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            ElementSMP.getInstance().reloadConfig();
            s.sendMessage("§a[ElementSMP] Config reloaded!");
            return true;
        }
        if (args.length >= 2) {
            Player t = Bukkit.getPlayer(args[1]);
            if (t != null) { ElementSMP.playerElements.put(t.getUniqueId(), args[2]); s.sendMessage("§aSet " + t.getName() + " to " + args[2]); }
        }
        return true;
    }
}
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled."); } return true; } }
class TrustCommand implements CommandExecutor { @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (!(s instanceof Player p) || a.length == 0) return false; Player target = Bukkit.getPlayer(a[0]); if (target == null) return false; Set<UUID> trusted = ElementSMP.trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()); if (c.getName().equalsIgnoreCase("trust")) { trusted.add(target.getUniqueId()); p.sendMessage("§aTrusted " + target.getName()); } else { trusted.remove(target.getUniqueId()); p.sendMessage("§cUntrusted " + target.getName()); } return true; } }
class AdminItemCommand implements CommandExecutor { @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (!(s instanceof Player p) || !s.isOp()) return true; ItemStack star = new ItemStack(Material.NETHER_STAR); ItemMeta m = star.getItemMeta(); m.setDisplayName(c.getName().contains("chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll"); star.setItemMeta(m); p.getInventory().addItem(star); return true; } }
