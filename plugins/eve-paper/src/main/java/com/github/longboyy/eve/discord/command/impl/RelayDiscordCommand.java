package com.github.longboyy.eve.discord.command.impl;

import com.github.longboyy.eve.EvePermissionHandler;
import com.github.longboyy.eve.EvePlugin;
import com.github.longboyy.eve.discord.command.AuthedDiscordCommand;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.dependencies.jda.api.Permission;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.OptionType;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.SubcommandData;
import vg.civcraft.mc.civmodcore.utilities.CivLogger;
import vg.civcraft.mc.namelayer.GroupManager;
import vg.civcraft.mc.namelayer.NameLayerAPI;
import vg.civcraft.mc.namelayer.group.Group;
import java.util.Objects;
import java.util.UUID;

public class RelayDiscordCommand extends AuthedDiscordCommand {

    public static final CommandData COMMAND_DATA = new CommandData("relay", "Used to managed relays")
        .addSubcommands(
            new SubcommandData("create", "Create a new relay")
                .addOption(OptionType.STRING, "group_name", "The name of the group to create the relay for", true),
            new SubcommandData("delete", "Delete an existing relay")
                .addOption(OptionType.STRING, "group_name", "The name of the group to delete the relay for", true));

    private final EvePlugin plugin;
    private final CivLogger logger;

    public RelayDiscordCommand(EvePlugin plugin){
        this.plugin = plugin;
        this.logger = CivLogger.getLogger(EvePlugin.class, RelayDiscordCommand.class);
    }

    @Override
    public CommandData getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    protected void execute(AuthedCommandContext ctx) {
        SlashCommandEvent event = ctx.getEvent();

        if(event.getGuild() == null){
            event.reply("This command must be executed in a discord server.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        if(!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)){
            event.getHook().sendMessage("You do not have permission to use this command!").queue();
            return;
        }

        String groupName = Objects.requireNonNull(event.getOption("group_name")).getAsString();
        Group group = GroupManager.getGroup(groupName);
        var textChannel = event.getTextChannel();
        if(group == null) {
            String msg = "The group `" + groupName + "` does not exist.";
            logger.warning(msg);
            event.getHook().sendMessage(msg).queue();
            return;
        }

        if(event.getSubcommandName().equals("create")){
            UUID playerUUID = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getUser().getId());
            if(!NameLayerAPI.getGroupManager().hasAccess(group, playerUUID, EvePermissionHandler.getCreateRelayPermission())){
                event.getHook().sendMessage("You do not have permission to create a relay for group " + groupName).queue();
                logger.warning("Player " + playerUUID + " does not have permission to create a relay for group " + groupName + " (" + group.getGroupId() + ")");
                return;
            }

            if(this.plugin.getRelayManager().createRelay(group.getGroupId(), textChannel.getGuild().getId(), textChannel.getId())){
                event.getHook().sendMessage("Created a relay for group `" + groupName + "`").queue();
            }else{
                event.getHook().sendMessage("Failed to create a relay for group `" + groupName + "` - make sure one doesn't already exist!").queue();
            }
            return;
        }

        if(this.plugin.getRelayManager().removeRelay(group.getGroupId(), textChannel.getId())){
            event.getHook().sendMessage("Deleted the relay for group `" + groupName + "` in this channel.").queue();
            return;
        }

        event.getHook().sendMessage("Failed to delete the relay for group `" + groupName + "` in this channel.").queue();
    }
}
