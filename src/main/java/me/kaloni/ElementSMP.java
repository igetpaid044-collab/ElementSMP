package me.kaloni;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public class ElementSMP extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    public static final HashMap<UUID, String> playerElements = new HashMap<>();
    public static final HashMap<UUID, Boolean> hotkeysEnabled = new HashMap<>();
    public static final HashMap<UUID, Long> cd1 = new HashMap<>(), cd2 = new HashMap<>(), cdWind = new HashMap<>();
    public static final HashMap<UUID, Set<UUID>> trustedPlayers = new HashMap<>();
    
    private final Set<UUID> windJumpers = new HashSet<>();
    private final Set<Block> domainBlocks = new HashSet<>();
    private final HashMap<UUID, Location> trappedPlayers = new HashMap<>();
    private final List<String> elements = Arrays.asList("wind", "fire", "ice", "nature", "lightning", "void", "gravity", "water", "blood", "earth");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, this);
        
        String[] cmds = {"elemental", "ability", "controls", "trust"};
        for (String cmd : cmds) {
            getCommand(cmd).setExecutor(this);
            getCommand(cmd).setTabCompleter(this);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    updateHUD(p);
                    handlePassives(p);
                    if (trappedPlayers.containsKey(p.getUniqueId())) checkTraps(p);
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public void onDisable() { saveData(); }

    private void saveData() {
        ConfigurationSection s = getConfig().createSection("players");
        playerElements.forEach((uuid, el) -> s.set(uuid.toString(), el));
        saveConfig();
    }

    private void loadData() {
        ConfigurationSection s = getConfig().getConfigurationSection("players");
        if (s != null) s.getKeys(false).forEach(k -> playerElements.put(UUID.fromString(k), s.getString(k)));
    }

    // --- HUD & HELP ---
    private void updateHUD(Player p) {
        String el = getElement(p);
        String color = getElementColor(el);
        long now = System.currentTimeMillis();
        
        String t1 = formatCD(cd1.getOrDefault(p.getUniqueId(), 0L) - now);
        String t2 = formatCD(cd2.getOrDefault(p.getUniqueId(), 0L) - now);
        String tW = (el.equals("wind")) ? " §7| §fJump: " + formatCD(cdWind.getOrDefault(p.getUniqueId(), 0L) - now) : "";

        String hud = String.format("%s§l(%s) §b(%s %s)  §d(%s %s)%s", 
            color, el.toUpperCase(), getAbilityName(el, 1), t1, getAbilityName(el, 2), t2, tW);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(hud));
    }

    private void sendHelp(Player p) {
        p.sendMessage("§8§m-------§b Elemental Lore & Help §8§m-------");
        p.sendMessage("§f§lWIND: §7Passive: Fall Immunity. §bJump (15s CD) & Dash.");
        p.sendMessage("§c§lFIRE: §7Passive: Fire Immunity. §bFlame Burst & Inferno.");
        p.sendMessage("§b§lICE: §7Passive: Ice Speed. §bFreeze Nova.");
        p.sendMessage("§2§lNATURE: §7Passive: No Hunger. §bVine Grapple.");
        p.sendMessage("§e§lLIGHTNING: §7Passive: Speed II. §bDash.");
        p.sendMessage("§8§lVOID/§5§lGRAVITY: §7Passive: Stealth/SlowFall. §dDOMAIN.");
        p.sendMessage("§3§lWATER: §7Passive: Breathing. §bSwim Boost.");
        p.sendMessage("§4§lBLOOD: §7Passive: Strength. §bLifesteal.");
        p.sendMessage("§6§lEARTH: §7Passive: Resistance. §bShield.");
        p.sendMessage("§8§m----------------------------------");
    }

    private String getElementColor(String el) {
        return switch (el) {
            case "fire" -> "§c"; case "ice" -> "§b"; case "nature" -> "§2";
            case "lightning" -> "§e"; case "void" -> "§8"; case "gravity" -> "§5";
            case "water" -> "§3"; case "blood" -> "§4"; case "earth" -> "§6"; default -> "§f";
        };
    }

    private String getAbilityName(String el, int slot) {
        if (slot == 1) return el.equals("nature") ? "Vine" : "Dash";
        return (el.equals("void") || el.equals("gravity")) ? "Domain" : "Ultimate";
    }

    private String formatCD(long ms) {
        return ms <= 0 ? "§aREADY" : String.format("§6%ds", ms/1000);
    }

    // --- WIND COOLDOWN & DOUBLE JUMP ---
    @EventHandler
    public void onFly(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || !getElement(p).equals("wind")) return;
        
        e.setCancelled(true);
        p.setAllowFlight(false);
        long now = System.currentTimeMillis();
        
        if (cdWind.getOrDefault(p.getUniqueId(), 0L) > now) {
            p.sendMessage("§cDouble Jump is on cooldown for " + (cdWind.get(p.getUniqueId()) - now)/1000 + "s!");
            return;
        }

        p.setVelocity(p.getLocation().getDirection().multiply(1.4).setY(0.8));
        p.playSound(p.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.2f);
        windJumpers.add(p.getUniqueId());
        cdWind.put(p.getUniqueId(), now + 15000);
    }

    // --- PASSIVES & DOMAINS ---
    private void handlePassives(Player p) {
        String el = getElement(p);
        if (el.equals("wind") && p.isOnGround()) p.setAllowFlight(true);
        if (el.equals("lightning")) p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, false, false));
        if (el.equals("void")) p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
        if (el.equals("gravity")) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false));
        
        if (windJumpers.contains(p.getUniqueId())) {
            if (p.isOnGround()) windJumpers.remove(p.getUniqueId());
            else p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
        }
    }

    private void checkTraps(Player p) {
        Location center = trappedPlayers.get(p.getUniqueId());
        if (p.getLocation().distance(center) > 5.5) p.teleport(center);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (hotkeysEnabled.getOrDefault(e.getPlayer().getUniqueId(), true)) {
            e.setCancelled(true);
            triggerAbility(e.getPlayer(), e.getPlayer().isSneaking() ? 2 : 1);
        }
    }

    public void triggerAbility(Player p, int slot) {
        String el = getElement(p); long now = System.currentTimeMillis();
        if ((slot == 1 && cd1.getOrDefault(p.getUniqueId(), 0L) > now) || (slot == 2 && cd2.getOrDefault(p.getUniqueId(), 0L) > now)) return;

        if (slot == 1) {
            p.setVelocity(p.getLocation().getDirection().multiply(1.5).setY(0.2));
            cd1.put(p.getUniqueId(), now + 5000);
        } else if (el.equals("void") || el.equals("gravity")) {
            spawnDomain(p.getLocation(), el);
            cd2.put(p.getUniqueId(), now + 60000);
        }
    }

    private void spawnDomain(Location center, String el) {
        Material mat = el.equals("void") ? Material.BLACK_STAINED_GLASS : Material.PURPLE_STAINED_GLASS;
        for (Player t : Bukkit.getOnlinePlayers()) {
            if (t.getLocation().distance(center) <= 6) {
                // Check if the user is trusted by the domain caster
                // For now, traps everyone; logic can be added here
                trappedPlayers.put(t.getUniqueId(), center.clone());
            }
        }
        new BukkitRunnable() {
            @Override public void run() { trappedPlayers.clear(); }
        }.runTaskLater(this, 200L);
    }

    // --- COMMAND HANDLER ---
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;

        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length > 0 && a[0].equalsIgnoreCase("help")) { sendHelp(p); return true; }
            if (a.length >= 3 && a[0].equalsIgnoreCase("set") && s.isOp()) {
                Player t = Bukkit.getPlayer(a[1]);
                if (t != null) {
                    playerElements.put(t.getUniqueId(), a[2].toLowerCase());
                    p.sendMessage("§aSet " + t.getName() + " to " + a[2]);
                }
                return true;
            }
            sendHelp(p); // Default to help if usage is wrong
        }

        if (c.getName().equalsIgnoreCase("trust") && a.length > 0) {
            Player t = Bukkit.getPlayer(a[0]);
            if (t != null) {
                trustedPlayers.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>()).add(t.getUniqueId());
                p.sendMessage("§aTrusted " + t.getName());
            }
            return true;
        }

        if (c.getName().equalsIgnoreCase("controls")) {
            boolean b = !hotkeysEnabled.getOrDefault(p.getUniqueId(), true);
            hotkeysEnabled.put(p.getUniqueId(), b);
            p.sendMessage("§bHotkeys: " + (b ? "§aON" : "§cOFF"));
        }
        
        if (c.getName().equalsIgnoreCase("ability") && a.length > 0) {
            triggerAbility(p, a[0].equals("2") ? 2 : 1);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("elemental")) {
            if (a.length == 1) return Arrays.asList("set", "help");
            if (a.length == 3) return elements;
        }
        if (c.getName().equalsIgnoreCase("ability")) return Arrays.asList("1", "2");
        return null;
    }

    public String getElement(Player p) { return playerElements.getOrDefault(p.getUniqueId(), "wind"); }
}
