package com.github.longboyy.eve.discord;

import com.github.longboyy.eve.EvePlugin;
import com.github.longboyy.eve.discord.command.impl.InviteDiscordCommand;
import com.github.longboyy.eve.discord.command.impl.RelayDiscordCommand;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.commands.PluginSlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommandProvider;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import github.scarsz.discordsrv.dependencies.jda.api.entities.Guild;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;

public class DiscordCommandManager implements SlashCommandProvider {

    private static boolean IS_SETUP = false;

    private final EvePlugin plugin;

    private final Map<String, DiscordCommand> commands = new HashMap<>();

    public DiscordCommandManager(EvePlugin plugin){
        this.plugin = plugin;
        //DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId()
        commands.put(RelayDiscordCommand.COMMAND_DATA.getName(), new RelayDiscordCommand(this.plugin));
        commands.put(InviteDiscordCommand.COMMAND_DATA.getName(), new InviteDiscordCommand());
        //commands.put(RelayConfigDiscordCommand.COMMAND_DATA.getName(), new RelayConfigDiscordCommand());
    }

    public void registerCommands(){
        if(!IS_SETUP) {
            DiscordSRV.api.addSlashCommandProvider(this);
        }
        DiscordSRV.api.updateSlashCommands();
        IS_SETUP = true;
    }

    public void registerCommandsForGuild(Guild guild){
        guild.updateCommands().addCommands(getCommandData()).queue();
    }

    @SlashCommand(path = "*")
    public void onSlashCommand(SlashCommandEvent event){
        if(!commands.containsKey(event.getName())){
            return;
        }

        commands.get(event.getName()).handle(event);
    }

    @Override
    public Set<PluginSlashCommand> getSlashCommands() {
        return commands.values().stream().map(command -> new PluginSlashCommand(plugin, command.getCommandData())).collect(Collectors.toSet());
    }

    private CommandData[] getCommandData(){
        return commands.values().stream().map(DiscordCommand::getCommandData).toArray(CommandData[]::new);
    }
}
