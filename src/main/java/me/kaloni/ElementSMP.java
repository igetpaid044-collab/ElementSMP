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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
 * ElementSMP 1.21.8 - Sovereignty Edition
 * A high-performance framework for 11 unique elements and Boss AI.
 */
public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- Data Management ---
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
        
        // Command Registration
        getCommand("elemental").setExecutor(this);
        getCommand("controls").setExecutor(this);
        getCommand("spawnarchon").setExecutor(this);
        getCommand("ability").setExecutor(this);

        // --- Core Engine Pulse (Run every 2 ticks / 0.1 seconds) ---
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updatePlayerHUD(p);
                    handleElementalPassives(p);
                    handleVisualEffects(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) enforceDomainConstraint(p);
                }
                updateBossLogic();
            }
        }.runTaskTimer(this, 0L, 2L);

        getLogger().info("=========================================");
        getLogger().info(" ElementSMP Sovereignty Loaded (400+ Lines) ");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        saveData();
        domainBlocks.forEach(b -> b.setType(Material.AIR));
    }

    // --- ELEMENTAL LORE ENGINE ---
    private void showLore(Player p) {
        p.sendMessage("§8§m        §r §6§l ELEMENTAL SOVEREIGNTY §r §8§m        ");
        p.sendMessage("§7Choose your destiny. Each element grants a passive and two powers.");
        p.sendMessage("");
        sendElementLine(p, "WIND", "§f", "The Sky-Strider", "Gale Force", "Zephyr Tornado");
        sendElementLine(p, "FIRE", "§c", "The Eternal Flame", "Ignition Dash", "Solar Supernova");
        sendElementLine(p, "ICE", "§b", "The Frost-Bound", "Cryo Slide", "Glacial Tomb");
        sendElementLine(p, "NATURE", "§2", "The Bloom-Warden", "Ivy Hook", "Rooted Overgrowth");
        sendElementLine(p, "LIGHTNING", "§e", "The Static-God", "Volt Warp", "Electric Judgment");
        sendElementLine(p, "VOID", "§8", "The Abyss-Walker", "Rift Shift", "Void Domain");
        sendElementLine(p, "GRAVITY", "§5", "The Star-Eater", "Zero-G", "Black Hole Singularity");
        sendElementLine(p, "WATER", "§3", "The Tide-Caller", "Wave Dash", "Tsunami Wall");
        sendElementLine(p, "BLOOD", "§4", "The Crimson-Cursed", "Siphon Strike", "Sanguine Rage");
        sendElementLine(p, "EARTH", "§6", "The Stone-Lord", "Geode Pillar", "Tectonic Quake");
        sendElementLine(p, "CHRONO", "§d", "The Time-Weaver", "Time Warp", "Temporal Paradox");
        p.sendMessage("§8§m                                           ");
    }

    private void sendElementLine(Player p, String name, String color, String title, String a1, String a2) {
        p.sendMessage(color + "§l" + name + " §8» " + color + title + " §8| §f" + a1 + " §8/ §f" + a2);
    }

    // --- ABILITY LOGIC ---
    public void useAbility(Player p, int slot) {
        String el = getElement(p);
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();

        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        World w = p.getWorld();
        if (slot == 1) { // Primary Ability (30s Cooldown)
            executePrimaryAbility(p, el, w);
            cd1.put(id, now + 30000);
        } else { // Ultimate Ability (60s Cooldown)
            executeUltimateAbility(p, el, w);
            cd2.put(id, now + 60000);
        }
    }

    private void executePrimaryAbility(Player p, String el, World w) {
        Vector dir = p.getLocation().getDirection().multiply(1.8).setY(1.0);
        switch (el) {
            case "void" -> p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(9)));
            case "nature" -> {
                RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 20);
                if (r != null) dir = r.getHitPosition().toLocation(w).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2.3);
            }
            case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 4));
            case "fire" -> w.spawnParticle(Particle.FLAME, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
            case "water" -> p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 1));
        }
        p.setVelocity(dir);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
    }

    private void executeUltimateAbility(Player p, String el, World w) {
        if (el.equals("void") || el.equals("gravity")) {
            createDomain(p.getLocation(), el);
        } else if (el.equals("fire")) {
            w.spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
            w.getNearbyEntities(p.getLocation(), 10, 10, 10).forEach(e -> e.setFireTicks(200));
        } else if (el.equals("lightning")) {
            w.getNearbyEntities(p.getLocation(), 8, 8, 8).forEach(e -> w.strikeLightning(e.getLocation()));
        } else if (el.equals("earth")) {
            w.getNearbyEntities(p.getLocation(), 12, 5, 12).forEach(e -> e.setVelocity(new Vector(0, 2.5, 0)));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 0.8f);
    }

    // --- DOMAIN SYSTEM ---
    private void createDomain(Location loc, String el) {
        Material border = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> sessionBlocks = new ArrayList<>();
        
        Bukkit.getOnlinePlayers().stream()
            .filter(op -> op.getLocation().distance(loc) <= 6.5)
            .forEach(op -> trappedPlayers.put(op.getUniqueId(), loc.clone()));

        for (int x = -7; x <= 7; x++) {
            for (int y = -7; y <= 7; y++) {
                for (int z = -7; z <= 7; z++) {
                    double dist = loc.clone().add(x, y, z).distance(loc);
                    if (dist > 6.8 && dist < 7.2) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType() == Material.AIR) {
                            b.setType(border);
                            domainBlocks.add(b);
                            sessionBlocks.add(b);
                        }
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                sessionBlocks.forEach(b -> { b.setType(Material.AIR); domainBlocks.remove(b); });
                trappedPlayers.entrySet().removeIf(e -> e.getValue().equals(loc));
            }
        }.runTaskLater(this, 300L); // 15 Seconds
    }

    private void enforceDomainConstraint(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 6.5) {
            p.teleport(center);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 0.5f);
        }
    }

    // --- BOSS AI SYSTEM ---
    private void updateBossLogic() {
        Iterator<Map.Entry<UUID, String>> it = activeBosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            LivingEntity boss = (LivingEntity) Bukkit.getEntity(entry.getKey());
            
            if (boss == null || boss.isDead()) {
                it.remove();
                continue;
            }

            // Shuffle Element every 7 seconds
            if (Math.random() < 0.015) {
                String next = elements.get(new Random().nextInt(elements.size()));
                activeBosses.put(boss.getUniqueId(), next);
                boss.setCustomName(getElementColor(next) + "§lArchon of " + next.toUpperCase());
                styleBossArmor(boss, next);
                boss.getWorld().spawnParticle(Particle.FLASH, boss.getLocation(), 2);
            }

            // Combat Logic
            if (boss.getTarget() != null && Math.random() < 0.08) {
                applyBossAttack(boss, entry.getValue());
            }
        }
    }

    private void applyBossAttack(LivingEntity boss, String el) {
        LivingEntity target = boss.getTarget();
        if (target == null) return;
        switch (el) {
            case "lightning" -> boss.getWorld().strikeLightningEffect(target.getLocation());
            case "gravity" -> target.setVelocity(new Vector(0, 1.6, 0));
            case "ice" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4));
            case "fire" -> target.setFireTicks(100);
            case "void" -> boss.teleport(target.getLocation().add(0, 1, 0));
        }
    }

    private void styleBossArmor(LivingEntity boss, String el) {
        Color c = getRawColor(el);
        boss.getEquipment().setHelmet(dye(Material.LEATHER_HELMET, c));
        boss.getEquipment().setChestplate(dye(Material.LEATHER_CHESTPLATE, c));
        boss.getEquipment().setLeggings(dye(Material.LEATHER_LEGGINGS, c));
        boss.getEquipment().setBoots(dye(Material.LEATHER_BOOTS, c));
    }

    private ItemStack dye(Material m, Color c) {
        ItemStack item = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(c);
        item.setItemMeta(meta);
        return item;
    }

    // --- HUD & PASSIVE PROCESSING ---
    private void updatePlayerHUD(Player p) {
        String el = getElement(p);
        if (el.equals("none")) return;

        long now = System.currentTimeMillis();
        String c1 = formatCD(cd1.getOrDefault(p.getUniqueId(), 0L) - now);
        String c2 = formatCD(cd2.getOrDefault(p.getUniqueId(), 0L) - now);
        String color = getElementColor(el);

        String hud = String.format("%s§l%s §8| §bSkill 1: %s §8| §dUltimate: %s", 
                color, el.toUpperCase(), c1, c2);
        
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    private void handleElementalPassives(Player p) {
        String el = getElement(p);
        if (el.equals("wind")) {
            if (p.isOnGround() && cdWind.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis()) p.setAllowFlight(true);
        } else if (p.getGameMode() == GameMode.SURVIVAL) {
            p.setAllowFlight(false); p.setFlying(false);
        }

        switch (el) {
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 1, false, false));
            case "blood" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 45, 0, false, false));
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, false, false));
            case "water" -> p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 45, 0, false, false));
        }
    }

    private void handleVisualEffects(Player p) {
        if (windJumpers.contains(p.getUniqueId())) {
            if (p.isOnGround()) windJumpers.remove(p.getUniqueId());
            else p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
        }
    }

    // --- EVENT LISTENERS ---
    @EventHandler
    public void onWindFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        e.setCancelled(true);
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        p.setVelocity(new Vector(0, 1.4, 0));
        p.getWorld().spawnParticle(Particle.WIND_CHARGE, p.getLocation(), 15);
        windJumpers.add(p.getUniqueId());
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 10000);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
        useAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    @EventHandler
    public void onFall(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && getElement(p).equals("wind") && e.getCause() == EntityDamageEvent.DamageCause.FALL) e.setCancelled(true);
    }

    // --- COMMAND SYSTEM ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        switch (c.getName().toLowerCase()) {
            case "elemental":
                if (a.length >= 3 && a[0].equalsIgnoreCase("set") && p.isOp()) {
                    Player target = Bukkit.getPlayer(a[1]);
                    if (target != null) {
                        playerElements.put(target.getUniqueId(), a[2].toLowerCase());
                        target.sendMessage("§aElement set to §l" + a[2].toUpperCase());
                    }
                } else showLore(p);
                break;
            case "spawnarchon":
                if (p.isOp()) {
                    WitherSkeleton boss = (WitherSkeleton) p.getWorld().spawnEntity(p.getLocation(), EntityType.WITHER_SKELETON);
                    boss.getAttributes().getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
                    boss.setHealth(1000.0);
                    activeBosses.put(boss.getUniqueId(), "wind");
                    styleBossArmor(boss, "wind");
                    Bukkit.broadcastMessage("§6§l[!] §eThe Elemental Archon has descended!");
                }
                break;
        }
        return true;
    }

    // --- DATA UTILITIES ---
    private String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "none"); }
    private String getElementColor(String el) {
        return switch (el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2"; case "lightning" -> "§e";
            case "void" -> "§8"; case "gravity" -> "§5"; case "water" -> "§3"; case "blood" -> "§4";
            case "earth" -> "§6"; case "chrono" -> "§d"; default -> "§f";
        };
    }
    private Color getRawColor(String el) {
        return switch (el) {
            case "fire" -> Color.RED; case "ice" -> Color.AQUA; case "nature" -> Color.GREEN;
            case "lightning" -> Color.YELLOW; case "void" -> Color.BLACK; case "gravity" -> Color.PURPLE;
            case "water" -> Color.BLUE; case "blood" -> Color.MAROON; case "earth" -> Color.ORANGE;
            case "chrono" -> Color.FUCHSIA; default -> Color.WHITE;
        };
    }
    private String formatCD(long ms) { return ms <= 0 ? "§aREADY" : "§6" + (ms/1000) + "s"; }

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
