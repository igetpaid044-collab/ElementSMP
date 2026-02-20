package me.kaloni;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Arrays;

public class PowerCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            openElementMenu((Player) sender);
            return true;
        }
        return false;
    }

    public void openElementMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 18, "§8All 12 Elements");

        inv.addItem(createItem(Material.BLAZE_POWDER, "§cFire", "§7Master of Heat"));
        inv.addItem(createItem(Material.WATER_BUCKET, "§bWater", "§7Master of Tides"));
        inv.addItem(createItem(Material.GRASS_BLOCK, "§aEarth", "§7Master of Nature"));
        inv.addItem(createItem(Material.FEATHER, "§fAir", "§7Master of Wind"));
        inv.addItem(createItem(Material.SNOWBALL, "§3Ice", "§7Master of Frost"));
        inv.addItem(createItem(Material.OAK_SAPLING, "§2Nature", "§7Master of Growth"));
        inv.addItem(createItem(Material.GOLDEN_SWORD, "§eLightning", "§7Master of Storms"));
        inv.addItem(createItem(Material.COAL, "§8Shadow", "§7Master of Darkness"));
        inv.addItem(createItem(Material.GLOWSTONE_DUST, "§eLight", "§7Master of Radiance"));
        inv.addItem(createItem(Material.MAGMA_CREAM, "§6Magma", "§7Master of Lava"));
        inv.addItem(createItem(Material.OBSIDIAN, "§5Void", "§7Master of Nothingness"));
        inv.addItem(createItem(Material.WHITE_STAINED_GLASS_PANE, "§7Wind", "§7Master of Speed"));

        player.openInventory(inv);
    }

    private ItemStack createItem(Material mat, String name, String lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
