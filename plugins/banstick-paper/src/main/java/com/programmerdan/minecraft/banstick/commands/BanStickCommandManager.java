package com.programmerdan.minecraft.banstick.commands;

import co.aikar.commands.BukkitCommandExecutionContext;
import co.aikar.commands.CommandContexts;
import com.programmerdan.minecraft.banstick.BanStick;
import com.programmerdan.minecraft.banstick.commands.context.BanStickContexts;
import org.jetbrains.annotations.NotNull;
import vg.civcraft.mc.civmodcore.commands.CommandManager;

/**
 * Registers all of BanStick's commands via Aikar's Command Framework (ACF),
 * replacing the old plugin.yml {@code commands:} block + raw
 * {@link org.bukkit.command.CommandExecutor} registration.
 */
public class BanStickCommandManager extends CommandManager {

    public BanStickCommandManager(BanStick plugin) {
        super(plugin);
    }

    @Override
    public void registerContexts(@NotNull CommandContexts<BukkitCommandExecutionContext> contexts) {
        super.registerContexts(contexts);
        BanStickContexts.register(contexts, getCommandCompletions());
    }

    @Override
    public void registerCommands() {
        registerCommand(new BanStickCommand());
        registerCommand(new DoubleTapCommand());
        registerCommand(new ForgiveCommand());
        registerCommand(new BanSaveCommand());
        registerCommand(new LoveTapCommand());
        registerCommand(new TakeItBackCommand());
        registerCommand(new DowsingRodCommand());
        registerCommand(new DrillDownCommand());
        registerCommand(new UntangleCommand());
        registerCommand(new GetAltsCommand());
        registerCommand(new BanRegistrarCommand());
    }

    /**
     * Note: Don't try to remove this in favour of a private field as
     * registerCommands() is called from the super constructor so the field
     * will not yet be assigned.
     *
     * @return Returns the plugin attached to this registrar.
     */
    @Override
    public BanStick getPlugin() {
        return (BanStick) super.getPlugin();
    }

}
