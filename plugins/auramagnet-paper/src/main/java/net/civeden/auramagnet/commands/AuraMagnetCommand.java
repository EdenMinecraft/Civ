package net.civeden.auramagnet.commands;

import java.util.List;
import net.civeden.auramagnet.AuraMagnet;
import net.civeden.auramagnet.listeners.HoeTagListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import vg.civcraft.mc.civmodcore.inventory.items.ItemUtils;

/**
 * /auramagnet give [hoe-type] - gives a tagged Magnet Hoe, restricted to
 * auramagnet.give.
 */
public class AuraMagnetCommand implements CommandExecutor, TabCompleter {

    private static final List<String> HOE_ARGS = List.of(
        "wooden", "stone", "iron", "golden", "diamond", "netherite"
    );

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (!player.hasPermission("auramagnet.give")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        Material hoeMaterial = Material.DIAMOND_HOE;
        if (args.length >= 2) {
            hoeMaterial = switch (args[1].toLowerCase()) {
                case "wooden" -> Material.WOODEN_HOE;
                case "stone" -> Material.STONE_HOE;
                case "iron" -> Material.IRON_HOE;
                case "golden" -> Material.GOLDEN_HOE;
                case "netherite" -> Material.NETHERITE_HOE;
                default -> Material.DIAMOND_HOE;
            };
        }

        ItemStack hoe = buildMagnetHoe(hoeMaterial);
        HoeTagListener.tryInject(hoe);

        var leftover = player.getInventory().addItem(hoe);
        leftover.values().forEach(i -> player.getWorld().dropItem(player.getLocation(), i));

        player.sendMessage(ChatColor.GOLD + "[AuraMagnet] " + ChatColor.WHITE + "Gave you a " + ChatColor.GOLD
            + hoeMaterial.name().replace("_", " ").toLowerCase() + ChatColor.WHITE + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("give");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return HOE_ARGS;
        }
        return List.of();
    }

    private ItemStack buildMagnetHoe(Material material) {
        AuraMagnet plugin = AuraMagnet.getInstance();
        ItemStack hoe = new ItemStack(material);

        ItemUtils.setComponentDisplayName(hoe, Component.text("Aura Magnet Hoe", NamedTextColor.GOLD));
        ItemUtils.setComponentLore(hoe, List.of(
            Component.text(plugin.getMagnetLoreTrigger(), NamedTextColor.GOLD),
            Component.text("Pulls nearby crops into your inventory.", NamedTextColor.GRAY),
            Component.text("Hold in offhand to activate.", NamedTextColor.GRAY)
        ));

        return hoe;
    }
}
