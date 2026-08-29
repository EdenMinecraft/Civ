package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import com.programmerdan.minecraft.banstick.BanStick;
import org.bukkit.command.CommandSender;

@CommandAlias("bansave|bsbs")
@CommandPermission("banstick.ips")
public class BanSaveCommand extends BaseCommand {

    @Default
    @Description("Flush cached changes straight to DB.")
    public void onBanSave(CommandSender sender) {
        BanStick.getPlugin().saveCache();
    }

}
