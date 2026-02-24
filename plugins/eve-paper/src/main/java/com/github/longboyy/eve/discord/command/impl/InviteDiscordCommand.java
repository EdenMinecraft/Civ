package com.github.longboyy.eve.discord.command.impl;

import com.github.longboyy.eve.discord.command.BaseDiscordCommand;
import com.github.longboyy.eve.discord.context.CommandContext;
import github.scarsz.discordsrv.dependencies.jda.api.Permission;
import github.scarsz.discordsrv.dependencies.jda.api.interactions.commands.build.CommandData;

public class InviteDiscordCommand extends BaseDiscordCommand<CommandContext> {

    public static final CommandData COMMAND_DATA = new CommandData("invite", "Get an invite link to add the bot to your own server");

    @Override
    public CommandData getCommandData() {
        return COMMAND_DATA;
    }

    @Override
    protected void execute(CommandContext ctx) {
        ctx.getEvent().reply(
            String.format("Add the bot to your discord with the following link: %s", ctx.getEvent().getJDA().getInviteUrl(Permission.ADMINISTRATOR))
        ).queue();
    }
}
