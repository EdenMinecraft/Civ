package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Gets all transitive shares for a particular player (alt group).
 *
 * @author Maxopoly
 */
@CommandAlias("getalts")
@CommandPermission("banstick.alts.view")
public class GetAltsCommand extends BaseCommand {

    @Default
    @Syntax("<name/uuid>")
    @Description("Gets alts for a player")
    @CommandCompletion("@banstickPlayers")
    public void onGetAlts(CommandSender sender, BSPlayer player) {
        Set<BSPlayer> directAssoc = player.getTransitiveSharedPlayers(true);
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD + "Directly associated accounts for " + player.getName() + " are: ");
        for (BSPlayer alt : directAssoc) {
            sb.append(alt.getName());
            sb.append("  ");
        }
        sender.sendMessage(sb.toString());

        Set<BSPlayer> ignoredAssoc = player.getTransitiveSharedPlayers(false);
        sb = new StringBuilder();
        sb.append(ChatColor.GOLD + "Associated accounts split off through exclusions are: ");
        for (BSPlayer alt : ignoredAssoc) {
            if (directAssoc.contains(alt)) {
                continue;
            }
            sb.append(alt.getName());
            sb.append("  ");
        }
        sender.sendMessage(sb.toString());
    }

}
