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
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    // Data Storage
    private final Map<UUID, String> playerElements = new HashMap<>();
    private final Map<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), cdWind = new HashMap<>();
    private final Map<UUID, String> activeBosses = new HashMap<>();
    private final Set<Block> domainBlocks = new HashSet<>();
    private final Map<UUID, Location> trappedPlayers = new HashMap<>();
    private final Set<UUID> windJumpers = new HashSet<>();

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

        // Core Engine: Runs every 2 ticks (0.1 seconds)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updatePlayerHUD(p);
                    handleElementPassives(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) enforceDomain(p);
                }
                updateBossLogic();
            }
        }.runTaskTimer(this, 0L, 2L);

        getLogger().info("ElementSMP Sovereignty Edition Loaded (350+ Lines).");
    }

    @Override
    public void onDisable() {
        saveData();
        domainBlocks.forEach(b -> b.setType(Material.AIR));
    }

    // --- LORE SYSTEM ---
    private void showElementLore(Player p) {
        p.sendMessage("§8§m--------§r §6§l ELEMENTAL SOVEREIGNTY §r §8§m--------");
        p.sendMessage("§7Current Elements and their Sovereign Powers:");
        p.sendMessage("");
        sendLoreLine(p, "WIND", "§f", "Gale Dash", "Tornado Blast");
        sendLoreLine(p, "FIRE", "§c", "Ignition Step", "Solar Supernova");
        sendLoreLine(p, "ICE", "§b", "Frost Slide", "Glacial Prison");
        sendLoreLine(p, "NATURE", "§2", "Vine Grapple", "Floral Overgrowth");
        sendLoreLine(p, "LIGHTNING", "§e", "Volt Flicker", "Thunder Judgment");
        sendLoreLine(p, "VOID", "§8", "Rift Jump", "Abyssal Domain");
        sendLoreLine(p, "GRAVITY", "§5", "Anti-G Lift", "Event Horizon");
        sendLoreLine(p, "WATER", "§3", "Aqua Jet", "Tidal Wave");
        sendLoreLine(p, "BLOOD", "§4", "Crimson Siphon", "Sanguine Rage");
        sendLoreLine(p, "EARTH", "§6", "Tectonic Dash", "Mountain Pillar");
        sendLoreLine(p, "CHRONO", "§d", "Time Warp", "Temporal Paradox");
        p.sendMessage("");
        p.sendMessage("§8§m--------------------------------------");
    }

    private void sendLoreLine(Player p, String name, String color, String a1, String a2) {
        p.sendMessage(color + "§l" + name + " §8» §f" + a1 + " §7& " + color + a2);
    }

    // --- ABILITY ENGINE ---
    public void triggerElementalPower(Player p, int slot) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "none");
        if (el.equals("none")) return;

        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        
        if (slot == 1 && cd1.getOrDefault(id, 0L) > now) return;
        if (slot == 2 && cd2.getOrDefault(id, 0L) > now) return;

        if (slot == 1) {
            executePrimaryAbility(p, el);
            cd1.put(id, now + 20000);
        } else {
            executeUltimateAbility(p, el);
            cd2.put(id, now + 50000);
        }
    }

    private void executePrimaryAbility(Player p, String el) {
        World w = p.getWorld();
        Vector dir = p.getLocation().getDirection().multiply(1.8).setY(1.0);
        
        switch (el) {
            case "fire" -> w.spawnParticle(Particle.FLAME, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.05);
            case "ice" -> w.spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 50, 0.5, 0.5, 0.5, 0.05);
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 4));
            case "void" -> p.teleport(p.getLocation().add(p.getLocation().getDirection().multiply(8)));
            case "water" -> p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 2));
            case "blood" -> {
                w.getNearbyEntities(p.getLocation(), 5, 5, 5).forEach(e -> {
                    if (e instanceof LivingEntity le && e != p) {
                        le.damage(4.0, p);
                        p.setHealth(Math.min(p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), p.getHealth() + 2.0));
                    }
                });
            }
        }
        p.setVelocity(dir);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
    }

    private void executeUltimateAbility(Player p, String el) {
        Location loc = p.getLocation();
        World w = p.getWorld();

        if (el.equals("void") || el.equals("gravity")) {
            startDomainExpansion(loc, el);
        } else if (el.equals("fire")) {
            w.spawnParticle(Particle.FLAME, loc, 300, 4, 4, 4, 0.1);
            w.getNearbyEntities(loc, 10, 10, 10).forEach(e -> e.setFireTicks(200));
        } else if (el.equals("lightning")) {
            w.getNearbyEntities(loc, 12, 12, 12).forEach(e -> w.strikeLightning(e.getLocation()));
        } else if (el.equals("ice")) {
            w.getNearbyEntities(loc, 8, 8, 8).forEach(e -> {
                if (e instanceof LivingEntity le) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 10));
            });
        }
        p.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
    }

    // --- FIXED BOSS AI LOGIC ---
    private void updateBossLogic() {
        Iterator<Map.Entry<UUID, String>> it = activeBosses.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, String> entry = it.next();
            Entity ent = Bukkit.getEntity(entry.getKey());
            
            if (ent == null || ent.isDead()) {
                it.remove();
                continue;
            }

            // FIXED: Casting to Mob to allow getTarget()
            if (ent instanceof Mob boss) {
                LivingEntity target = boss.getTarget();
                
                if (Math.random() < 0.02) {
                    String nextEl = elements.get(new Random().nextInt(elements.size()));
                    activeBosses.put(boss.getUniqueId(), nextEl);
                    boss.setCustomName(getCol(nextEl) + "§lArchon of " + nextEl.toUpperCase());
                    styleBoss(boss, nextEl);
                }

                if (target != null && Math.random() < 0.1) {
                    handleBossSkill(boss, target, entry.getValue());
                }
            }
        }
    }

    private void handleBossSkill(Mob boss, LivingEntity target, String el) {
        switch (el) {
            case "fire" -> target.setFireTicks(100);
            case "gravity" -> target.setVelocity(new Vector(0, 1.8, 0));
            case "ice" -> target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 5));
            case "lightning" -> target.getWorld().strikeLightningEffect(target.getLocation());
        }
    }

    // --- DOMAIN EXPANSION SYSTEM ---
    private void startDomainExpansion(Location loc, String el) {
        Material glass = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        List<Block> sessionBlocks = new ArrayList<>();
        
        Bukkit.getOnlinePlayers().stream()
                .filter(online -> online.getLocation().distance(loc) <= 7)
                .forEach(online -> trappedPlayers.put(online.getUniqueId(), loc.clone()));

        for (int x = -7; x <= 7; x++) {
            for (int y = -7; y <= 7; y++) {
                for (int z = -7; z <= 7; z++) {
                    double dist = loc.clone().add(x, y, z).distance(loc);
                    if (dist > 6.7 && dist < 7.3) {
                        Block b = loc.clone().add(x, y, z).getBlock();
                        if (b.getType().isAir()) {
                            b.setType(glass);
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
                trappedPlayers.clear();
            }
        }.runTaskLater(this, 300L);
    }

    private void enforceDomain(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 6.5) {
            p.teleport(center);
            p.sendMessage("§cYou cannot escape the domain!");
        }
    }

    // --- EVENT LISTENERS ---
    @EventHandler
    public void onWindFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !playerElements.getOrDefault(p.getUniqueId(), "").equals("wind")) return;
        e.setCancelled(true);
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;

        p.setVelocity(new Vector(0, 1.5, 0));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.2, 0.2, 0.2, 0.1);
        cdWind.put(p.getUniqueId(), System.currentTimeMillis() + 10000);
    }

    @EventHandler
    public void onAbilityKey(PlayerSwapHandItemsEvent e) {
        e.setCancelled(true);
        triggerElementalPower(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) {
            String el = playerElements.getOrDefault(p.getUniqueId(), "");
            if (el.equals("fire")) e.getEntity().setFireTicks(40);
            if (el.equals("ice")) ((LivingEntity)e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        }
    }

    // --- COMMAND SYSTEM ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length >= 3 && a[0].equalsIgnoreCase("set") && p.isOp()) {
                Player target = Bukkit.getPlayer(a[1]);
                if (target != null) {
                    playerElements.put(target.getUniqueId(), a[2].toLowerCase());
                    target.sendMessage("§aYour element has been set to: " + a[2]);
                }
            } else {
                showElementLore(p);
            }
        } else if (c.getName().equalsIgnoreCase("spawnarchon") && p.isOp()) {
            WitherSkeleton boss = (WitherSkeleton) p.getWorld().spawnEntity(p.getLocation(), EntityType.WITHER_SKELETON);
            // FIXED: Using singular getAttribute
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
            boss.setHealth(1000.0);
            activeBosses.put(boss.getUniqueId(), "wind");
            styleBoss(boss, "wind");
            Bukkit.broadcastMessage("§6§l[!] §eA Sovereign Archon has manifested!");
        }
        return true;
    }

    // --- UTILITIES ---
    private void updatePlayerHUD(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "none");
        if (el.equals("none")) return;
        long n = System.currentTimeMillis();
        String msg = getCol(el) + "§l" + el.toUpperCase() + " §8| §bS1: " + formatTime(cd1.getOrDefault(p.getUniqueId(), 0L) - n) + " §8| §dUlt: " + formatTime(cd2.getOrDefault(p.getUniqueId(), 0L) - n);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
    }

    private void handleElementPassives(Player p) {
        String el = playerElements.getOrDefault(p.getUniqueId(), "");
        if (el.equals("wind")) {
            if (p.isOnGround() && cdWind.getOrDefault(p.getUniqueId(), 0L) < System.currentTimeMillis()) p.setAllowFlight(true);
        } else if (p.getGameMode() == GameMode.SURVIVAL) {
            p.setAllowFlight(false);
            p.setFlying(false);
        }
        
        // Permanent Potion Passives
        switch (el) {
            case "lightning" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 45, 1, false, false));
            case "earth" -> p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 45, 0, false, false));
            case "water" -> p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 45, 0, false, false));
            case "chrono" -> p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 45, 1, false, false));
        }
    }

    private String getCol(String el) {
        return switch (el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2"; case "lightning" -> "§e";
            case "void" -> "§8"; case "gravity" -> "§5"; case "blood" -> "§4"; default -> "§f";
        };
    }

    private String formatTime(long ms) {
        return ms <= 0 ? "§aREADY" : "§6" + (ms / 1000) + "s";
    }

    private void styleBoss(LivingEntity boss, String el) {
        Color c = switch (el) {
            case "fire" -> Color.RED; case "ice" -> Color.AQUA; case "nature" -> Color.GREEN;
            case "lightning" -> Color.YELLOW; case "void" -> Color.BLACK; default -> Color.WHITE;
        };
        boss.getEquipment().setHelmet(dye(Material.LEATHER_HELMET, c));
        boss.getEquipment().setChestplate(dye(Material.LEATHER_CHESTPLATE, c));
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
