package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

    public static HashMap<UUID, String> playerElements = new HashMap<>();
    public static HashMap<UUID, String> secondaryElements = new HashMap<>();
    public static HashMap<UUID, Boolean> useHotkeys = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility1 = new HashMap<>();
    public static HashMap<UUID, Long> cdAbility2 = new HashMap<>();
    
    private final List<String> elementList = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Ocean", "Psychic", "Gravity");

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("ability").setExecutor(new AbilityHandler());
        getCommand("controls").setExecutor(new ControlToggle());
        getCommand("give-reroll").setExecutor(new AdminItemCommand());
        getCommand("give-chaos").setExecutor(new AdminItemCommand());
        
        AdminElementHandler adminHandler = new AdminElementHandler(this);
        getCommand("elementsmp").setExecutor(adminHandler);
        getCommand("elementsmp").setTabCompleter(adminHandler);

        // Constant Particle/Passive Loop
        new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                angle += 0.15;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateActionBarHUD(p);
                    spawnElementalHalo(p, angle);
                    applyPassives(p);
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void applyPassives(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind").toLowerCase();
        if (el.equals("gravity")) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, false, false));
        if (el.equals("blood")) p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 45, 0, false, false));
    }

    private void spawnElementalHalo(Player p, double angle) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind");
        Location loc = p.getLocation().add(0, 2.1, 0);
        double x = Math.cos(angle) * 0.7;
        double z = Math.sin(angle) * 0.7;
        loc.add(x, 0, z);

        // Enhanced Particle Visibility
        Particle part = getParticleFor(el);
        if (part == Particle.DUST) {
            p.getWorld().spawnParticle(Particle.DUST, loc, 3, 0.05, 0.05, 0.05, new Particle.DustOptions(getDustColor(el), 1.2f));
        } else {
            p.getWorld().spawnParticle(part, loc, 5, 0.02, 0.02, 0.02, 0.05);
        }
    }

    private Particle getParticleFor(String el) {
        return switch (el.toLowerCase()) {
            case "fire" -> Particle.FLAME;
            case "nature" -> Particle.HAPPY_VILLAGER;
            case "water", "ocean" -> Particle.BUBBLE;
            case "gravity", "void" -> Particle.PORTAL;
            case "lightning" -> Particle.ELECTRIC_SPARK;
            default -> Particle.DUST;
        };
    }

    private Color getDustColor(String el) {
        return switch (el.toLowerCase()) {
            case "blood" -> Color.RED;
            case "ice" -> Color.AQUA;
            case "wind" -> Color.WHITE;
            case "earth" -> Color.MAROON;
            default -> Color.GRAY;
        };
    }

    public static void triggerAbility(Player p, int num) {
        UUID id = p.getUniqueId();
        String el = playerElements.getOrDefault(id, "Wind").toLowerCase();
        HashMap<UUID, Long> cdMap = (num == 1) ? cdAbility1 : cdAbility2;

        if (cdMap.getOrDefault(id, 0L) > System.currentTimeMillis()) {
            p.sendMessage("§c" + (num == 1 ? "A1" : "A2") + " Cooldown: " + ((cdMap.get(id) - System.currentTimeMillis()) / 1000) + "s");
            return;
        }

        if (num == 1 && el.equals("nature")) {
            performNatureGrapple(p);
            cdMap.put(id, System.currentTimeMillis() + 120000);
        } else if (num == 2 && el.equals("gravity")) {
            performGravityMeteor(p, (JavaPlugin) Bukkit.getPluginManager().getPlugin("ElementSMP"));
            cdMap.put(id, System.currentTimeMillis() + 60000);
        }
    }

    private static void performNatureGrapple(Player p) {
        RayTraceResult result = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 35);
        if (result != null && result.getHitBlock() != null) {
            Location hit = result.getHitPosition().toLocation(p.getWorld());
            
            // DRAW VISIBLE VINE
            Vector start = p.getEyeLocation().toVector();
            Vector end = hit.toVector();
            double dist = start.distance(end);
            Vector dir = end.subtract(start).normalize();
            
            for (double i = 0; i < dist; i += 0.3) {
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, start.clone().add(dir.clone().multiply(i)).toLocation(p.getWorld()), 2, 0.1, 0.1, 0.1, 0.02);
                p.getWorld().spawnParticle(Particle.COMPOSTER, start.clone().add(dir.clone().multiply(i)).toLocation(p.getWorld()), 1, 0.05, 0.05, 0.05, 0);
            }

            Vector pull = hit.toVector().subtract(p.getLocation().toVector()).normalize().multiply(2.5);
            p.setVelocity(pull.setY(0.7));
            p.playSound(p.getLocation(), Sound.BLOCK_VINE_STEP, 1.5f, 0.8f);
        } else {
            p.sendMessage("§cToo far away!");
        }
    }

    private static void performGravityMeteor(Player p, JavaPlugin plugin) {
        List<Block> tempBlocks = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(20, 20, 20)) {
            if (e instanceof Player target && !target.equals(p)) {
                Location loc = target.getLocation();
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) {
                        for (int y = 0; y <= 2; y++) {
                            if (Math.abs(x) == 1 || Math.abs(z) == 1) {
                                Block b = loc.clone().add(x, y, z).getBlock();
                                if (b.getType().isAir()) { b.setType(Material.COBBLESTONE); tempBlocks.add(b); }
                            }
                        }
                    }
                }
            }
        }
        new BukkitRunnable() { @Override public void run() { for (Block b : tempBlocks) b.setType(Material.AIR); } }.runTaskLater(plugin, 140L);

        new BukkitRunnable() {
            int timer = 0;
            @Override public void run() {
                if (timer > 10) { this.cancel(); return; }
                for (Entity e : p.getNearbyEntities(20, 20, 20)) {
                    if (e instanceof LivingEntity && !e.equals(p)) {
                        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, e.getLocation().add(0, 10, 0), 10, 0.5, 0.5, 0.5, 0.1);
                        FallingBlock rock = e.getWorld().spawnFallingBlock(e.getLocation().add(0, 10, 0), Material.STONE.createBlockData());
                        rock.setDropItem(false); rock.setHurtEntities(true);
                        rock.setMetadata("meteor", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    }
                }
                timer++;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    @EventHandler public void onMeteorHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof FallingBlock rock && rock.hasMetadata("meteor")) e.setDamage(4.0);
    }

    private void updateActionBarHUD(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "Wind");
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b§lElement: §f" + el.toUpperCase()));
    }

    @EventHandler public void onReroll(PlayerInteractEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.NETHER_STAR) {
            e.setCancelled(true);
            boolean isChaos = e.getItem().getItemMeta().getDisplayName().contains("Chaos");
            startRerollAnimation(e.getPlayer(), isChaos);
            e.getItem().setAmount(e.getItem().getAmount() - 1);
        }
    }

    @EventHandler public void onFKey(PlayerSwapHandItemsEvent event) { 
        if (useHotkeys.getOrDefault(event.getPlayer().getUniqueId(), false)) {
            event.setCancelled(true);
            triggerAbility(event.getPlayer(), event.getPlayer().isSneaking() ? 2 : 1);
        }
    }

    public void startRerollAnimation(Player p, boolean forceChaos) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (ticks >= 40) {
                    String finalEl = elementList.get(new Random().nextInt(elementList.size()));
                    playerElements.put(p.getUniqueId(), finalEl);
                    if (forceChaos) {
                        secondaryElements.put(p.getUniqueId(), "Gravity");
                        Bukkit.broadcastMessage("§5§lCHAOS! §d" + p.getName() + " has awakened Dual Powers!");
                    }
                    p.sendTitle("§a Awakened:", "§2" + finalEl, 10, 40, 10);
                    this.cancel(); return;
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1, 2);
                ticks++;
            }
        }.runTaskTimer(this, 0L, 2L);
    }
}

// --- UPDATED COMMAND HANDLER WITH TAB COMPLETION ---
class AdminElementHandler implements CommandExecutor, TabCompleter {
    private final ElementSMP plugin;
    private final List<String> elements = Arrays.asList("Wind", "Fire", "Water", "Earth", "Lightning", "Void", "Ice", "Nature", "Blood", "Ocean", "Psychic", "Gravity");
    
    public AdminElementHandler(ElementSMP plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!s.isOp()) return true;
        if (args.length >= 2 && args[0].equalsIgnoreCase("set")) {
            Player t = (args.length == 3) ? Bukkit.getPlayer(args[1]) : (s instanceof Player ? (Player)s : null);
            String el = (args.length == 3) ? args[2] : args[1];
            if (t != null) {
                ElementSMP.playerElements.put(t.getUniqueId(), el);
                s.sendMessage("§aSet " + t.getName() + " to " + el);
            }
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (args.length == 1) return Collections.singletonList("set");
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) return null; // Returns online players
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return elements;
        return Collections.emptyList();
    }
}

class AdminItemCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] args) {
        if (!(s instanceof Player p) || !s.isOp()) return true;
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = star.getItemMeta();
        m.setDisplayName(c.getName().equals("give-chaos") ? "§5§lChaos Reroll" : "§b§lElemental Reroll");
        star.setItemMeta(m); p.getInventory().addItem(star); return true;
    }
}
class AbilityHandler implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) ElementSMP.triggerAbility(p, (a.length > 0 && a[0].equals("2")) ? 2 : 1); return true; } }
class ControlToggle implements CommandExecutor { public boolean onCommand(CommandSender s, Command c, String l, String[] a) { if (s instanceof Player p) { ElementSMP.useHotkeys.put(p.getUniqueId(), !ElementSMP.useHotkeys.getOrDefault(p.getUniqueId(), false)); p.sendMessage("§bHotkeys Toggled!"); } return true; } }
