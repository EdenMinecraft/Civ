package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Syntax;
import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.data.BSIP;
import com.programmerdan.minecraft.banstick.data.BSIPData;
import com.programmerdan.minecraft.banstick.data.BSPlayer;
import com.programmerdan.minecraft.banstick.data.BSRegistrars;
import com.programmerdan.minecraft.banstick.data.BSSession;
import com.programmerdan.minecraft.banstick.handler.BanHandler;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

@CommandAlias("banprovider")
@CommandPermission("banstick.banprovider")
public class BanRegistrarCommand extends BaseCommand {

    @Default
    @Syntax("<playerName> [undo]")
    @Description("(Un-)Bans the entire provider of the last connection used by a player based on what its registered as")
    @CommandCompletion("@banstickPlayers")
    public void onBanProvider(CommandSender sender, BSPlayer player, @Optional String undo) {
        boolean isUndo = undo != null;
        BSSession lastSession = player.getLatestSession();
        BSIP ip = lastSession.getIP();
        List<BSIPData> proxyChecks = BSIPData.allByIP(ip);
        BSRegistrars handler = BanStick.getPlugin().getRegistrarHandler();
        for (BSIPData data : proxyChecks) {
            if (data.getRegisteredAs() == null || data.getRegisteredAs().isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Can not ban registrar, because none was known");
                continue;
            }
            if (isUndo) {
                handler.unbanRegistrar(data);
                sender.sendMessage(
                    ChatColor.GREEN + "Forgave registrar " + data.getRegisteredAs() + " of " + data.toString());
            } else {
                handler.banRegistrar(data);
                sender.sendMessage(
                    ChatColor.GREEN + "Banning registrar " + data.getRegisteredAs() + " of " + data.toString());
            }
        }
        if (!isUndo) {
            //also give them an ip ban on the way if they dont have one already
            BanHandler.doIPBan(ip, null, null, true, false);
        }
    }

}
