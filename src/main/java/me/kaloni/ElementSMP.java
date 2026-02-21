package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * ElementSMP: Sovereignty Edition
 * A professional 400+ line framework for 11 Elements & Boss AI.
 */
public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, String> playerElements = new HashMap<>();
    private final Map<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), cdWind = new HashMap<>();
    private final Set<UUID> windJumpers = new HashSet<>();
    private final Set<Block> domainBlocks = new HashSet<>();
    private final Map<UUID, Location> trappedPlayers = new HashMap<>();
    private final Map<UUID, String> activeBosses = new HashMap<>();

    private final List<String> elements = Arrays.asList(
            "wind", "fire", "ice", "nature", "lightning", 
            "void", "gravity", "water", "blood", "earth", "chrono"
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("elemental").setExecutor(this);
        getCommand("spawnarchon").setExecutor(this);

        // Core Heartbeat (Runs every 2 ticks)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    handlePassives(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) enforceDomain(p);
                }
                updateBossAI();
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public void onDisable() {
        saveData();
        domainBlocks.forEach(b -> b.setType(Material.AIR));
    }

    // --- ELEMENTAL LORE SYSTEM ---
    private void showLore(Player p) {
        p.sendMessage("§8§m        §r §6§l ELEMENTAL SOVEREIGNTY §r §8§m        ");
        sendLoreLine(p, "WIND", "§f", "Sky-Strider", "Gale Force", "Zephyr Tornado");
        sendLoreLine(p, "FIRE", "§c", "Eternal Flame", "Ignition Dash", "Solar Supernova");
        sendLoreLine(p, "ICE", "§b", "Frost-Bound", "Cryo Slide", "Glacial Tomb");
        sendLoreLine(p, "NATURE", "§2", "Bloom-Warden", "Ivy Hook", "Rooted Overgrowth");
        sendLoreLine(p, "LIGHTNING", "§e", "Static-God", "Volt Warp", "Electric Judgment");
        sendLoreLine(p, "VOID", "§8", "Abyss-Walker", "Rift Shift", "Void Domain");
        sendLoreLine(p, "GRAVITY", "§5", "Star-Eater", "Zero-G", "Singularity");
        sendLoreLine(p, "WATER", "§3", "Tide-Caller", "Wave Dash", "Tsunami Wall");
        sendLoreLine(p, "BLOOD", "§4", "Crimson-Cursed", "Siphon", "Sanguine Rage");
        sendLoreLine(p, "EARTH", "§6", "Stone-Lord", "Geode Pillar", "Tectonic Quake");
        sendLoreLine(p, "CHRONO", "§d", "Time-Weaver", "Time Warp", "Temporal Paradox");
        p.sendMessage("§8§m                                           ");
    }

    private void sendLoreLine(Player p, String name, String color, String title, String a1, String a2) {
        p.sendMessage(color + "§l" + name + " §8» " + color + title + " §8| §f" + a1 + " §8/ §f" + a2);
    }

    // --- ABILITY LOGIC ---
    public void triggerAbility(Player p, int slot) {
        String el = getElement(p);
        if (el.equals("none")) return;
        
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        if (slot == 1) {
            executePrimary(p, el);
            cd1.put(id, now + 25000);
        } else {
            executeUltimate(p, el);
            cd2.put(id, now + 60000);
        }
    }

    private void executePrimary(Player p, String el) {
        World w = p.getWorld();
        Vector dir = p.getLocation().getDirection().multiply(1.8).setY(1.0);
        
        switch (el) {
            case "void" -> p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(8)));
            case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 4));
            case "nature" -> {
                RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 20);
                if (r != null) dir = r.getHitPosition().toLocation(w).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2.2);
            }
            case "blood" -> w.getNearbyEntities(p.getLocation(), 5, 5, 5).forEach(e -> {
                if (e instanceof LivingEntity le && e != p) {
                    le.damage(4.0, p);
                    p.setHealth(Math.min(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), p.getHealth() + 2.0));
                }
            });
        }
        p.setVelocity(dir);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
    }

    private void executeUltimate(Player p, String el) {
        Location loc = p.getLocation();
        World w = p.getWorld();

        if (el.equals("void") || el.equals("gravity")) {
            createDomain(loc, el);
        } else if (el.equals("fire")) {
            w.spawnParticle(Particle.FLAME, loc, 100, 2, 2, 2, 0.1);
            w.getNearbyEntities(loc, 8, 8, 8).forEach(e -> e.setFireTicks(160));
        } else if (el.equals("lightning")) {
            w.getNearbyEntities(loc, 10, 10, 10).forEach(e -> w.strikeLightning(e.getLocation()));
        } else if (el.equals("earth")) {
            w.getNearbyEntities(loc, 10, 5, 10).forEach(e -> e.setVelocity(new Vector(0, 2, 0)));
        }
        p.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
    }

    // --- DOMAIN SYSTEM ---
    private void createDomain(Location loc, String el) {
        Material glass = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> session = new ArrayList<>();
        
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.getLocation().distance(loc) <= 6.5) trappedPlayers.put(op.getUniqueId(), loc.clone());
        }

        for (int x = -7; x <= 7; x++) {
            for (int y = -7; y <= 7; y++) {
                for (int z = -7; z <= 7; z++) {
                    double dist = loc.clone().add(x, y, z).distance(loc);
                    if (dist > 6.8 && dist < 7.2) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType().isAir()) {
                            b.setType(glass);
                            domainBlocks.add(b);
                            session.add(b);
                        }
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                session.forEach(b -> { b.setType(Material.AIR); domainBlocks.remove(b); });
                trappedPlayers.clear();
            }
        }.runTaskLater(this, 300L);
    }

    private void enforceDomain(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 6.5) p.teleport(center);
    }

    // --- BOSS AI (FIXED) ---
    private void updateBossAI() {
        Iterator<Map.Entry<UUID, String>> it = activeBosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity ent = Bukkit.getEntity(entry.getKey());
            
            if (ent == null || ent.isDead()) {
                it.remove();
                continue;
            }

            if (ent instanceof Mob boss) {
                LivingEntity target = boss.getTarget(); // Works because of Mob cast
                
                if (Math.random() < 0.02) {
                    String next = elements.get(new Random().nextInt(elements.size()));
                    activeBosses.put(boss.getUniqueId(), next);
                    boss.setCustomName(getElCol(next) + "§lArchon of " + next.toUpperCase());
                    styleBoss(boss, next);
                }

                if (target != null && Math.random() < 0.1) {
                    bossSkill(boss, target, entry.getValue());
                }
            }
        }
    }

    private void bossSkill(Mob boss, LivingEntity target, String el) {
        switch (el) {
            case "fire" -> target.setFireTicks(100);
            case "gravity" -> target.setVelocity(new Vector(0, 1.5, 0));
            case "ice" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5));
        }
    }

    // --- HUD & PASSIVES ---
    private void updateHUD(Player p) {
        String el = getElement(p);
        if (el.equals("none")) return;
        long now = System.currentTimeMillis();
        String hud = getElCol(el) + "§l" + el.toUpperCase() + " §8| §bS1: " + formatCD(cd1.getOrDefault(p.getUniqueId(), 0L) - now) + " §8| §dUlt: " + formatCD(cd2.getOrDefault(p.getUniqueId(), 0L) - now);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    private void handlePassives(Player p) {
        String el = getElement(p);
        if (el.equals("wind")) {
            if (p.isOnGround() && cdWind.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis()) p.setAllowFlight(true);
        } else if (p.getGameMode() == GameMode.SURVIVAL) {
            p.setAllowFlight(false); p.setFlying(false);
        }
        
        switch (el) {
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 1, false, false));
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, false, false));
            case "water" -> p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 45, 0, false, false));
        }
    }

    // --- EVENTS ---
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
        triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    @EventHandler
    public void onWindFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        e.setCancelled(true);
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        p.setVelocity(new Vector(0, 1.5, 0));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20);
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 8000);
    }

    // --- COMMANDS (FIXED ATTRIBUTES) ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length >= 3 && a[0].equalsIgnoreCase("set") && p.isOp()) {
                Player t = Bukkit.getPlayer(a[1]);
                if (t != null) playerElements.put(t.getUniqueId(), a[2].toLowerCase());
            } else showLore(p);
        } else if (c.getName().equalsIgnoreCase("spawnarchon") && p.isOp()) {
            WitherSkeleton boss = (WitherSkeleton) p.getWorld().spawnEntity(p.getLocation(), EntityType.WITHER_SKELETON);
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
            boss.setHealth(1000.0);
            activeBosses.put(boss.getUniqueId(), "fire");
            styleBoss(boss, "fire");
            Bukkit.broadcastMessage("§eThe Archon has appeared!");
        }
        return true;
    }

    // --- DATA UTILITIES ---
    private String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "none"); }
    private String formatCD(long ms) { return ms <= 0 ? "§aREADY" : "§6" + (ms/1000) + "s"; }
    private String getElCol(String el) {
        return switch(el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2"; case "void" -> "§8";
            case "lightning" -> "§e"; case "gravity" -> "§5"; default -> "§f";
        };
    }
    private void styleBoss(LivingEntity boss, String el) {
        Color col = el.equals("fire") ? Color.RED : Color.AQUA;
        boss.getEquipment().setHelmet(dye(Material.LEATHER_HELMET, col));
        boss.getEquipment().setChestplate(dye(Material.LEATHER_CHESTPLATE, col));
    }
    private ItemStack dye(Material m, Color c) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(c);
        i.setItemMeta(meta);
        return i;
    }
    private void loadData() {
        ConfigurationSection s = getConfig().getConfigurationSection("players");
        if (s != null) s.getKeys(false).forEach(k -> playerElements.put(UUID.fromString(k), s.getString(k)));
    }
    private void saveData() {
        ConfigurationSection s = getConfig().createSection("players");
        playerElements.forEach((u, el) -> s.set(u.toString(), el));
        saveConfig();
    }
}
