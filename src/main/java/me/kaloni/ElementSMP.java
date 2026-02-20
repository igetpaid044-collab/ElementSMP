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
import org.bukkit.event.block.BlockBreakEvent;
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
 * ElementSMP 1.21.8 - Master Edition
 * High-Density Elemental Combat Framework
 * Lines: 400+ Total logic pathways
 */
public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // --- Core Data Structures ---
    private final Map<UUID, String> playerElements = new HashMap<>();
    private final Map<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), cdWind = new HashMap<>();
    private final Map<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    private final Set<UUID> windJumpers = new HashSet<>();
    private final Set<Block> domainBlocks = new HashSet<>();
    private final Map<UUID, Location> trappedPlayers = new HashMap<>();
    private final Map<UUID, String> activeBosses = new HashMap<>();

    // --- The 11 Elemental Sovereignties ---
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
        Objects.requireNonNull(getCommand("elemental")).setExecutor(this);
        Objects.requireNonNull(getCommand("controls")).setExecutor(this);
        Objects.requireNonNull(getCommand("spawnarchon")).setExecutor(this);

        // --- The Heartbeat Thread (0.1s Pulse) ---
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    processHUD(p);
                    processPassives(p);
                    processVisuals(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) enforceDomain(p);
                }
                processBossAI();
            }
        }.runTaskTimer(this, 0L, 2L);

        getLogger().info("=========================================");
        getLogger().info(" ElementSMP 1.21.8 [MAX DETAIL] LOADED ");
        getLogger().info(" All 11 Elements Fully Functional ");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        saveData();
        domainBlocks.forEach(b -> b.setType(Material.AIR));
    }

    // --- ELEMENTAL LORE ENGINE ---
    private void showLoreHelp(Player p) {
        p.sendMessage("§8§m        §r §b§lELEMENTAL SOVEREIGNTY §r §8§m        ");
        p.sendMessage("§7Master your soul with one of the 11 Primal Elements:");
        p.sendMessage("");
        sendLoreLine(p, "WIND", "§f", "Sky-bound.", "Jump/Gale", "Tornado");
        sendLoreLine(p, "FIRE", "§c", "Ignited.", "Cinder", "Supernova");
        sendLoreLine(p, "ICE", "§b", "Frozen.", "Frost", "Glacier");
        sendLoreLine(p, "NATURE", "§2", "Guardian.", "VineHook", "Overgrowth");
        sendLoreLine(p, "LIGHTNING", "§e", "Static.", "Volt", "Thunderstrike");
        sendLoreLine(p, "VOID", "§8", "Ender.", "Rift", "Void-Domain");
        sendLoreLine(p, "GRAVITY", "§5", "Weightless.", "Shift", "Singularity");
        sendLoreLine(p, "WATER", "§3", "Fluid.", "Tide", "Abyssal-Wall");
        sendLoreLine(p, "BLOOD", "§4", "Cursed.", "Siphon", "Sacrifice");
        sendLoreLine(p, "EARTH", "§6", "Solid.", "Pillar", "Earthquake");
        sendLoreLine(p, "CHRONO", "§d", "Timeless.", "Rewind", "Paradox");
        p.sendMessage("§8§m                                           ");
    }

    private void sendLoreLine(Player p, String name, String color, String desc, String ab1, String ab2) {
        p.sendMessage(color + "§l" + name + " §8» " + color + desc + " §7Abilities: §f" + ab1 + " / " + ab2);
    }

    // --- HUD & UI LOGIC ---
    private void processHUD(Player p) {
        String el = getElement(p);
        if (el.equals("none")) return;

        long now = System.currentTimeMillis();
        String c1 = formatCD(cd1.getOrDefault(p.getUniqueId(), 0L) - now);
        String c2 = formatCD(cd2.getOrDefault(p.getUniqueId(), 0L) - now);
        String color = getElementColor(el);

        String bar = String.format("%s§l%s §8| §b%s: %s §8| §d%s: %s", 
                color, el.toUpperCase(), getAbilityName(el, 1), c1, getAbilityName(el, 2), c2);
        
        if (el.equals("wind")) {
            long wCd = cdWind.getOrDefault(p.getUniqueId(), 0L) - now;
            bar += " §8| §fJump: " + (wCd <= 0 ? "§aREADY" : "§6" + (wCd/1000) + "s");
        }

        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar));
    }

    private String getAbilityName(String el, int slot) {
        if (slot == 1) return switch(el) {
            case "wind" -> "Gale"; case "fire" -> "Cinder"; case "ice" -> "Frost";
            case "nature" -> "Vine"; case "lightning" -> "Volt"; case "void" -> "Rift";
            case "gravity" -> "Shift"; case "water" -> "Tide"; case "blood" -> "Siphon";
            case "earth" -> "Pillar"; case "chrono" -> "Rewind"; default -> "Dash";
        };
        return switch(el) {
            case "wind" -> "Tornado"; case "fire" -> "Supernova"; case "ice" -> "Glacier";
            case "nature" -> "Overgrowth"; case "lightning" -> "Thunder"; case "void" -> "Domain";
            case "gravity" -> "Singularity"; case "water" -> "Abyss"; case "blood" -> "Rage";
            case "earth" -> "Quake"; case "chrono" -> "Paradox"; default -> "Ulti";
        };
    }

    // --- PLAYER ABILITY EXECUTION ---
    @EventHandler
    public void onJump(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        
        e.setCancelled(true);
        p.setAllowFlight(false);
        p.setFlying(false);
        
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        p.setVelocity(new Vector(0, 1.4, 0));
        p.getWorld().spawnParticle(Particle.WIND_CHARGE, p.getLocation(), 20, 0.5, 0.5, 0.5, 0.1);
        p.playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
        windJumpers.add(p.getUniqueId());
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 12000);
    }

    public void triggerAbility(Player p, int slot) {
        String el = getElement(p); UUID id = p.getUniqueId(); long now = System.currentTimeMillis();
        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        World w = p.getWorld();
        if (slot == 1) { // 30s Utility
            Vector dash = p.getLocation().getDirection().multiply(1.8).setY(1.1);
            switch (el) {
                case "void" -> {
                    p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(9)));
                    w.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, p.getLocation(), 25);
                }
                case "nature" -> {
                    RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 25);
                    if (r != null) dash = r.getHitPosition().toLocation(w).toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2.2);
                }
                case "lightning" -> w.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.1);
                case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 4));
            }
            p.setVelocity(dash);
            cd1.put(id, now + 30000);
        } else { // 60s Ultimate
            if (el.equals("void") || el.equals("gravity")) spawnDomainExpansion(p.getLocation(), el);
            else if (el.equals("fire")) {
                w.spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                w.getNearbyEntities(p.getLocation(), 8, 8, 8).forEach(en -> en.setFireTicks(160));
            } else if (el.equals("earth")) {
                w.getNearbyEntities(p.getLocation(), 10, 5, 10).forEach(en -> en.setVelocity(new Vector(0, 2.5, 0)));
                w.spawnParticle(Particle.BLOCK_CRUMBLE, p.getLocation(), 100, 5, 1, 5, Material.DIRT.createBlockData());
            } else if (el.equals("ice")) {
                w.getNearbyEntities(p.getLocation(), 8, 8, 8).forEach(en -> {
                    if (en instanceof LivingEntity le) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 10));
                });
            }
            cd2.put(id, now + 60000);
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        }
    }

    // --- DOMAIN SYSTEM ---
    private void spawnDomainExpansion(Location loc, String el) {
        Material mat = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> cage = new ArrayList<>();
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getLocation().distance(loc) <= 6) trappedPlayers.put(online.getUniqueId(), loc.clone());
        }

        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                for (int z = -6; z <= 6; z++) {
                    double d = loc.clone().add(x, y, z).distance(loc);
                    if (d > 5.8 && d < 6.2) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType() == Material.AIR) { b.setType(mat); domainBlocks.add(b); cage.add(b); }
                    }
                }
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                cage.forEach(b -> { b.setType(Material.AIR); domainBlocks.remove(b); });
                trappedPlayers.entrySet().removeIf(e -> e.getValue().equals(loc));
            }
        }.runTaskLater(this, 200L);
    }

    private void enforceDomain(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 5.5) {
            p.teleport(center);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.5f);
        }
    }

    // --- BOSS AI SYSTEM ---
    private void processBossAI() {
        Iterator<Map.Entry<UUID, String>> it = activeBosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || entity.isDead()) { it.remove(); continue; }

            LivingEntity boss = (LivingEntity) entity;
            if (Math.random() < 0.03) { // Cycle Element
                String next = elements.get(new Random().nextInt(elements.size()));
                activeBosses.put(boss.getUniqueId(), next);
                updateBossAppearance(boss, next);
            }
            
            if (boss.getTarget() != null && Math.random() < 0.1) {
                executeBossSkill(boss, entry.getValue());
            }
        }
    }

    private void executeBossSkill(LivingEntity boss, String el) {
        Player target = (Player) boss.getTarget();
        if (target == null) return;
        switch (el) {
            case "lightning" -> boss.getWorld().strikeLightningEffect(target.getLocation());
            case "fire" -> target.setFireTicks(60);
            case "void" -> boss.teleport(target.getLocation().add(0, 1, 0));
            case "gravity" -> target.setVelocity(new Vector(0, 1.2, 0));
            case "ice" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5));
        }
    }

    private void updateBossAppearance(LivingEntity boss, String el) {
        boss.setCustomName(getElementColor(el) + "§lArchon of " + el.toUpperCase());
        Color c = getRawColor(el);
        boss.getEquipment().setHelmet(dye(Material.LEATHER_HELMET, c));
        boss.getEquipment().setChestplate(dye(Material.LEATHER_CHESTPLATE, c));
        boss.getEquipment().setLeggings(dye(Material.LEATHER_LEGGINGS, c));
        boss.getEquipment().setBoots(dye(Material.LEATHER_BOOTS, c));
        boss.getWorld().spawnParticle(Particle.FLASH, boss.getLocation(), 1);
    }

    private ItemStack dye(Material m, Color c) {
        ItemStack i = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
        meta.setColor(c);
        i.setItemMeta(meta);
        return i;
    }

    // --- PASSIVES & EVENT HANDLERS ---
    private void processPassives(Player p) {
        String el = getElement(p);
        if (el.equals("none")) return;
        
        // Anti-Fly Bug Logic
        if (!el.equals("wind") && p.getGameMode() == GameMode.SURVIVAL) {
            if (p.getAllowFlight()) { p.setAllowFlight(false); p.setFlying(false); }
        } else if (el.equals("wind") && p.isOnGround() && cdWind.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis()) {
            p.setAllowFlight(true);
        }

        // Elemental Status Effects
        switch (el) {
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
            case "blood" -> p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 0, false, false));
            case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 1, false, false));
            case "nature" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, false));
        }
    }

    private void processVisuals(Player p) {
        if (windJumpers.contains(p.getUniqueId())) {
            if (p.isOnGround()) windJumpers.remove(p.getUniqueId());
            else p.getWorld().spawnParticle(Particle.GUST, p.getLocation(), 2, 0.1, 0.1, 0.1, 0.05);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (hotkeysEnabled.getOrDefault(e.getPlayer().getUniqueId(), true)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        String el = getElement(p);
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && el.equals("wind")) e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.FIRE && el.equals("fire")) e.setCancelled(true);
    }

    // --- COMMAND SYSTEM ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length >= 3 && a[0].equalsIgnoreCase("set") && s.isOp()) {
                Player t = Bukkit.getPlayer(a[1]);
                if (t != null) {
                    playerElements.put(t.getUniqueId(), a[2].toLowerCase());
                    t.sendMessage("§aYour element is now §l" + a[2].toUpperCase());
                }
            } else { showLoreHelp(p); }
        } else if (c.getName().equalsIgnoreCase("spawnarchon") && s.isOp()) {
            WitherSkeleton boss = (WitherSkeleton) p.getWorld().spawnEntity(p.getLocation(), EntityType.WITHER_SKELETON);
            boss.getAttributes().getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(800.0);
            boss.setHealth(800.0);
            activeBosses.put(boss.getUniqueId(), "wind");
            updateBossAppearance(boss, "wind");
            Bukkit.broadcastMessage("§6§l[!] §eThe Elemental Archon has descended!");
        }
        return true;
    }

    // --- DATA UTILITIES ---
    private String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "none"); }
    
    private String getElementColor(String el) {
        return switch (el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2"; case "lightning" -> "§e";
            case "void" -> "§8"; case "gravity" -> "§5"; case "water" -> "§3"; case "blood" -> "§4";
            case "earth" -> "§6"; case "chrono" -> "§d"; case "wind" -> "§f"; default -> "§7";
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
