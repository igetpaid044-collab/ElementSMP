package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.enchantments.Enchantment;
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

    // Maps for Storage
    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, String> secondaryElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
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

        // Master Loop: Passives, Particles, HUD
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateActionBarHUD(p);
                    applyPassives(p);
                    if (secondaryElements.containsKey(p.getUniqueId())) {
                        p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
                    }
                }
            }
        }.runTaskTimer(this, 0L, 5L);
    }

    // --- REROLL SYSTEM ---
    public void startRerollAnimation(Player p, boolean isChaos) {
        new BukkitRunnable() {
            int ticks = 0;
            final Random rand = new Random();
            @Override public void run() {
                if (ticks < 30) {
                    String rolling = elementList.get(rand.nextInt(elementList.size()));
                    p.sendTitle("§7§l> §f§l" + rolling.toUpperCase() + " §7§l<", "§8Searching DNA...", 0, 5, 0);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f + (ticks * 0.03f));
                } 
                else if (ticks == 30 && isChaos) {
                    p.sendTitle("§5§kXXXXXX", "§d§lERR: NULL_POINTER", 0, 20, 5);
                    p.getWorld().strikeLightningEffect(p.getLocation());
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 2f);
                }
                else if (ticks == 45) {
                    String el1 = elementList.get(rand.nextInt(elementList.size()));
                    playerElements.put(p.getUniqueId(), el1);
                    if (isChaos) {
                        String el2 = elementList.get(rand.nextInt(elementList.size()));
                        secondaryElements.put(p.getUniqueId(), el2);
                        p.sendTitle("§d§l" + el1 + " §f& §d§l" + el2, "§5§k|| §fCHAOS AWAKENED §5§k||", 10, 60, 10);
                    } else {
                        secondaryElements.remove(p.getUniqueId());
                        p.sendTitle("§a§l" + el1.toUpperCase(), "§7Affinity Formed.", 10, 40, 10);
                    }
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    this.cancel();
                }
                ticks++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    // --- ABILITIES REGISTRY ---
    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toLowerCase();
        HashMap<UUID, Long> cdMap = (num == 1) ? cd1 : cd2;

        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            p.sendMessage("§cCooldown: " + (cdMap.get(id) - System.currentTimeMillis())/1000 + "s");
            return;
        }

        boolean success = false;
        if (num == 1) { // Primary Abilities
            success = switch (el) {
                case "nature" -> performNatureGrapple(p);
                case "void" -> performVoidWarp(p);
                case "fire" -> performFireBurst(p);
                case "lightning" -> performLightningDash(p);
                default -> false;
            };
        } else { // Ultimate Abilities
            success = switch (el) {
                case "ice" -> performAbsoluteZero(p);
                case "gravity" -> performGravityLift(p);
                default -> false;
            };
        }

        if (success) {
            cdMap.put(id, System.currentTimeMillis() + (num == 1 ? 15000 : 45000));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        }
    }

    // --- ABILITY LOGIC ---
    private static boolean performNatureGrapple(Player p) {
        RayTraceResult res = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
        if (res == null || res.getHitBlock() == null) return false;
        Location hit = res.getHitPosition().toLocation(p.getWorld());
        Vector dir = hit.toVector().subtract(p.getEyeLocation().toVector()).normalize();

        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                if (i > 15 || p.getLocation().distance(hit) < 1.5) { this.cancel(); return; }
                p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 15, 0.2, 0.2, 0.2, Material.MOSS_BLOCK.createBlockData());
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation(), 5, 0.4, 0.4, 0.4, 0);
                i++;
            }
        }.runTaskTimer(getInstance(), 0L, 1L);
        p.setVelocity(dir.multiply(2.1).setY(0.5));
        return true;
    }

    private static boolean performAbsoluteZero(Player p) {
        p.sendMessage("§b§lABSOLUTE ZERO");
        for (double i = 0; i < 2 * Math.PI; i += 0.2) {
            double x = Math.cos(i) * 10; double z = Math.sin(i) * 10;
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(x, 0.1, z), 5, 0.1, 0.1, 0.1, 0);
        }
        for (Entity e : p.getNearbyEntities(10, 5, 10)) {
            if (e instanceof Player t && t != p) {
                t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 100, 255, false, false));
                t.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 100, 200, false, false));
                t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, Material.ICE.createBlockData());
            }
        }
        return true;
    }

    private static boolean performVoidWarp(Player p) {
        Location loc = p.getEyeLocation().add(p.getLocation().getDirection().multiply(8));
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation(), 50, 0.5, 1, 0.5, 0.1);
        p.teleport(loc);
        p.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        return true;
    }

    private static boolean performFireBurst(Player p) {
        p.setVelocity(new Vector(0, 1.2, 0));
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation(), 100, 1, 1, 1, 0.1);
        p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 2, 0, 0, 0, 0);
        for (Entity e : p.getNearbyEntities(5, 5, 5)) { if (e instanceof LivingEntity le && e != p) le.setFireTicks(100); }
        return true;
    }

    private static boolean performLightningDash(Player p) {
        p.setVelocity(p.getLocation().getDirection().multiply(3).setY(0.2));
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.2);
        return true;
    }

    private static boolean performGravityLift(Player p) {
        for (Entity e : p.getNearbyEntities(8, 8, 8)) {
            if (e instanceof LivingEntity le) {
                le.setVelocity(new Vector(0, 1.5, 0));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
            }
        }
        return true;
    }

    // --- HUD ---
    private void updateActionBarHUD(Player p) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toUpperCase();
        if (secondaryElements.containsKey(id)) el = "§5§k|§d " + el + "/" + secondaryElements.get(id).toUpperCase() + " §5§k|";
        
        long c1 = (cd1.getOrDefault(id, 0L) - System.currentTimeMillis()) / 1000;
        long c2 = (cd2.getOrDefault(id, 0L) - System.currentTimeMillis()) / 1000;
        
        String msg = String.format("§b§l%s §8| §eA1: %s §8| §6A2: %s", el, (c1<=0?"§aREADY":"§c"+c1+"s"), (c2<=0?"§aREADY":"§c"+c2+"s"));
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    private void applyPassives(Player p) {
        applyEff(p, playerElements.getOrDefault(p.getUniqueId(), "Wind"));
        if (secondaryElements.containsKey(p.getUniqueId())) applyEff(p, secondaryElements.get(p.getUniqueId()));
    }

    private void applyEff(Player p, String el) {
        switch (el.toLowerCase()) {
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
            case "gravity" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, false, false));
            case "blood" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, false, false));
            case "nature" -> p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, false));
        }
    }

    @EventHandler public void onReroll(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && item.getType() == Material.NETHER_STAR && item.hasItemMeta()) {
            e.setCancelled(true);
            boolean chaos = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase().contains("chaos");
            startRerollAnimation(e.getPlayer(), chaos);
            item.setAmount(item.getAmount() - 1);
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
class AdminItemCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p) || !s.isOp()) return true;
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = star.getItemMeta();
        if (c.getName().contains("chaos")) {
            m.setDisplayName("§5§lChaos Reroll");
            m.addEnchant(Enchantment.LUCK, 1, true);
            m.setCustomModelData(1001); // ItemsAdder Icon 1
        } else {
            m.setDisplayName("§b§lElemental Reroll");
            m.setCustomModelData(1000); // ItemsAdder Icon 2
        }
        star.setItemMeta(m); p.getInventory().addItem(star); return true;
    }
}
class AdminElementHandler implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (s.isOp() && args.length >= 2) {
            Player t = Bukkit.getPlayer(args[1]);
            if (t != null) { ElementSMP.playerElements.put(t.getUniqueId(), args[2]); s.sendMessage("§aUpdated."); }
        }
        return true;
    }
}
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled."); } return true; } }
