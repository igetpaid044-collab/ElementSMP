package me.kaloni;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class ElementSMP extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("elements").setExecutor(new PowerCommand());
        
        // Register the Ability Command
        AbilityCommand abilityCmd = new AbilityCommand();
        getCommand("ability").setExecutor(abilityCmd);
    }
}

class AbilityCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;
        String element = ElementSMP.playerElements.getOrDefault(player.getUniqueId(), "None");

        if (args.length == 0) {
            player.sendMessage("§cUsage: /ability <1|2>");
            return true;
        }

        if (args[0].equals("1")) {
            handleAbilityOne(player, element);
        } else if (args[0].equals("2")) {
            handleAbilityTwo(player, element);
        }
        return true;
    }

    private void handleAbilityOne(Player p, String e) {
        switch (e) {
            case "Fire": p.launchProjectile(Fireball.class); break;
            case "Water": p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 200, 1)); break;
            case "Earth": p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 2)); break;
            case "Air": p.setVelocity(new Vector(0, 1.5, 0)); break;
            case "Ice": p.launchProjectile(Snowball.class); break;
            case "Nature": p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1)); break;
            case "Lightning": p.getWorld().strikeLightning(p.getTargetBlock(null, 10).getLocation()); break;
            case "Shadow": p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0)); break;
            case "Light": p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 600, 0)); break;
            case "Magma": p.setFireTicks(100); p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 200, 1)); break;
            case "Void": p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 1)); break;
            case "Wind": p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 3)); break;
            default: p.sendMessage("§cYou don't have an element!");
        }
    }

    private void handleAbilityTwo(Player p, String e) {
        switch (e) {
            case "Fire": p.getWorld().setInfiniburn(p.getLocation().getChunk().getChunkKey(), true); break;
            case "Water": p.setRemainingAir(p.getMaximumAir()); break;
            case "Earth": p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.COBBLESTONE, 16)); break;
            case "Air": p.setVelocity(p.getLocation().getDirection().multiply(2)); break;
            case "Ice": p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 10)); break;
            case "Nature": p.getWorld().generateTree(p.getLocation(), org.bukkit.TreeType.TREE); break;
            case "Lightning": p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 600, 2)); break;
            case "Shadow": p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0)); break;
            case "Light": p.setHealth(p.getMaxHealth()); break;
            case "Magma": p.getWorld().spawn(p.getLocation(), org.bukkit.entity.MagmaCube.class); break;
            case "Void": p.teleport(p.
