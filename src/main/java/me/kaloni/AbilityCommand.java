package me.kaloni;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class AbilityCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player p = (Player) sender;
        String e = ElementSMP.playerElements.getOrDefault(p.getUniqueId(), "None");

        if (args.length == 0) {
            p.sendMessage("§cUsage: /ability <1|2>");
            return true;
        }

        if (args[0].equals("1")) {
            useAbilityOne(p, e);
        } else if (args[0].equals("2")) {
            useAbilityTwo(p, e);
        }
        return true;
    }

    private void useAbilityOne(Player p, String e) {
        p.sendMessage("§6Ability 1 Activated!");
        switch (e) {
            case "Fire": p.launchProjectile(Fireball.class); break;
            case "Water": p.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 300, 1)); break;
            case "Earth": p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 300, 2)); break;
            case "Air": p.setVelocity(new Vector(0, 1.5, 0)); break;
            case "Ice": p.launchProjectile(Snowball.class); break;
            case "Nature": p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1)); break;
            case "Lightning": p.getWorld().strikeLightning(p.getTargetBlock(null, 15).getLocation()); break;
            case "Shadow": p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 300, 0)); break;
            case "Light": p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 1200, 0)); break;
            case "Magma": p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 1)); break;
            case "Void": p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1)); break;
            case "Wind": p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 300, 3)); break;
        }
    }

    private void useAbilityTwo(Player p, String e) {
        p.sendMessage("§6Ability 2 Activated!");
        switch (e) {
            case "Fire": p.setFireTicks(0); break;
            case "Water": p.setRemainingAir(p.getMaximumAir()); break;
            case "Earth": p.getInventory().addItem(new org.bukkit.inventory.ItemStack(Material.COBBLESTONE, 32)); break;
            case "Air": p.setVelocity(p.getLocation().getDirection().multiply(2.5)); break;
            case "Ice": p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 5)); break;
            case "Nature": p.getWorld().generateTree(p.getLocation(), org.bukkit.TreeType.TREE); break;
            case "Lightning": p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 1200, 2)); break;
            case "Shadow": p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0)); break;
            case "Light": p.setHealth(p.getMaxHealth()); break;
            case "Magma": p.getWorld().spawn(p.getLocation(), org.bukkit.entity.MagmaCube.class); break;
            case "Void": p.teleport(p.getLocation().add(0, 10, 0)); break;
            case "Wind": p.setFallDistance(0); break;
        }
    }
}
