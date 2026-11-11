package com.github.longboyy.eve.discord;

import com.github.longboyy.eve.EvePlugin;
import com.github.longboyy.eve.discord.command.impl.InviteDiscordCommand;
import com.github.longboyy.eve.discord.command.impl.RelayDiscordCommand;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.commands.PluginSlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommandProvider;
import github.scarsz.discordsrv.dependencies.jda.api.JDA;
import github.scarsz.discordsrv.dependencies.jda.api.events.interaction.SlashCommandEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Eve's slash commands are registered exclusively through DiscordSRV's provider API.
 *
 * DiscordSRV owns each guild's command list: {@link github.scarsz.discordsrv.api.ApiManager#updateSlashCommands()}
 * overwrites it with the union of every provider's commands. Eve must never call
 * {@code guild.updateCommands()} or {@code jda.updateCommands()} itself, as that either wipes other
 * plugins' commands or creates global duplicates of the guild commands.
 */
public class DiscordCommandManager implements SlashCommandProvider {

    private final EvePlugin plugin;

    private final Map<String, DiscordCommand> commands = new HashMap<>();

    private boolean providerRegistered = false;
    private boolean staleGlobalsCleared = false;

    public DiscordCommandManager(EvePlugin plugin){
        this.plugin = plugin;
        commands.put(RelayDiscordCommand.COMMAND_DATA.getName(), new RelayDiscordCommand(this.plugin));
        commands.put(InviteDiscordCommand.COMMAND_DATA.getName(), new InviteDiscordCommand());
    }

    /**
     * Adds this provider to DiscordSRV. Safe to call before JDA is connected, so DiscordSRV's own
     * ready-time command sync already includes Eve's commands.
     */
    public void registerProvider(){
        if(providerRegistered){
            return;
        }
        DiscordSRV.api.addSlashCommandProvider(this);
        providerRegistered = true;
    }

    /**
     * Removes this provider from DiscordSRV. DiscordSRV keeps providers across plugin reloads, so without
     * this a reloaded Eve leaves its old instance behind and every command gets handled twice.
     */
    public void unregisterProvider(){
        if(!providerRegistered){
            return;
        }
        DiscordSRV.api.removeSlashCommandProvider(this);
        providerRegistered = false;
    }

    /**
     * Pushes the current command set to every guild. Requires JDA to be connected.
     */
    public void registerCommands(){
        registerProvider();
        clearStaleGlobalCommands();
        DiscordSRV.api.updateSlashCommands();
    }

    /**
     * Older Eve builds registered commands globally via {@code jda.updateCommands()}. Those global commands
     * persist on Discord and show up alongside the guild commands DiscordSRV registers, so users see every
     * Eve command twice. Delete any global command that shares a name with one of ours.
     */
    private void clearStaleGlobalCommands(){
        if(staleGlobalsCleared){
            return;
        }
        JDA jda = DiscordSRV.getPlugin().getJda();
        if(jda == null){
            return;
        }
        staleGlobalsCleared = true;
        jda.retrieveCommands().queue(globals -> globals.stream()
                .filter(command -> commands.containsKey(command.getName()))
                .forEach(command -> {
                    plugin.getLogger().info("Deleting stale global Discord command /" + command.getName());
                    command.delete().queue(null, error ->
                            plugin.getLogger().warning("Failed to delete global command /" + command.getName() + ": " + error.getMessage()));
                }),
                error -> {
                    staleGlobalsCleared = false;
                    plugin.getLogger().warning("Failed to fetch global Discord commands: " + error.getMessage());
                });
    }

    @SlashCommand(path = "*")
    public void onSlashCommand(SlashCommandEvent event){
        DiscordCommand command = commands.get(event.getName());
        if(command == null){
            return;
        }
        command.handle(event);
    }

    @Override
    public Set<PluginSlashCommand> getSlashCommands() {
        return commands.values().stream().map(command -> new PluginSlashCommand(plugin, command.getCommandData())).collect(Collectors.toSet());
    }
}
