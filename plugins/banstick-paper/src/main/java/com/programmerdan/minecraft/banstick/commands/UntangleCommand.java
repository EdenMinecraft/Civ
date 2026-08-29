package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Syntax;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.handler.ExclusionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

/**
 * Untangle command, creates a new altgraph based on imputs.
 *
 * @author Maxopoly
 */
@CommandAlias("untangle")
@CommandPermission("banstick.alts.modify")
public class UntangleCommand extends BaseCommand {

    @Default
    @Syntax("<name/uuid> [name/uuid] [name/uuid] ...")
    @Description("Reassigns alt groups")
    @CommandCompletion("@banstickPlayers")
    public void onUntangle(CommandSender sender, BSPlayer[] players) {
        Set<BSPlayer> subGraphPlayers = new HashSet<>(Arrays.asList(players));
        Set<BSPlayer> allGraphPlayers = new HashSet<>();
        // Determine all nodes (players) in the graph (association network) we are
        // creating a subgraph off
        for (BSPlayer player : subGraphPlayers) {
            allGraphPlayers.addAll(player.getTransitiveSharedPlayers(true));
        }

        int delCounter = 0;
        // Delete all preexisting exclusions within this graph. banstick-velocity
        // owns exclusion state, so this is a network call per pair rather than a
        // local cache mutation.
        List<BSPlayer> graphList = new ArrayList<>(allGraphPlayers);
        for (int i = 0; i < graphList.size(); i++) {
            for (int j = i + 1; j < graphList.size(); j++) {
                if (ExclusionHandler.hasExclusionWith(graphList.get(i).getUUID(), graphList.get(j).getUUID())) {
                    if (ExclusionHandler.deleteExclusion(graphList.get(i).getUUID(), graphList.get(j).getUUID())) {
                        delCounter++;
                    }
                }
            }
        }

        int createCounter = 0;
        // create exclusions between all players in the new subgraph and all players not
        // in the new subgraph
        Set<BSPlayer> outsideSubGraphPlayers = new HashSet<>(allGraphPlayers);
        outsideSubGraphPlayers.removeAll(subGraphPlayers);
        for (BSPlayer inside : subGraphPlayers) {
            for (BSPlayer outside : outsideSubGraphPlayers) {
                if (ExclusionHandler.createExclusion(inside.getUUID(), outside.getUUID())) {
                    createCounter++;
                }
            }
        }
        sender.sendMessage(ChatColor.GREEN + String.format(
            "Added exclusions to group containing %d players. %d exclusions were created and %d exclusions were deleted",
            allGraphPlayers.size(), createCounter, delCounter));
        StringBuilder sb = new StringBuilder();
        for (BSPlayer player : subGraphPlayers) {
            sb.append(player.getName());
            sb.append(":");
            sb.append(player.getUUID());
            sb.append("  ");
        }
        sender.sendMessage(ChatColor.GREEN
            + String.format("First group created contains %d players: %s", subGraphPlayers.size(), sb.toString()));

        sb = new StringBuilder();
        for (BSPlayer player : outsideSubGraphPlayers) {
            sb.append(player.getName());
            sb.append(":");
            sb.append(player.getUUID());
            sb.append("  ");
        }
        sender.sendMessage(ChatColor.GREEN + String.format("Second group created contains %d players: %s",
            outsideSubGraphPlayers.size(), sb.toString()));
    }

}
