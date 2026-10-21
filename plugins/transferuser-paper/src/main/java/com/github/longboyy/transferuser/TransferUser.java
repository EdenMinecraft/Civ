package com.github.longboyy.transferuser;

import com.github.longboyy.transferuser.commands.TransferUserCommand;
import com.github.longboyy.transferuser.groups.GroupTransfer;
import com.github.longboyy.transferuser.inventory.InventoryTransfer;
import com.janboerman.invsee.spigot.api.InvseePlusPlus;
import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.civmodcore.commands.CommandManager;

public class TransferUser extends ACivMod {

    private CommandManager commandManager;
    private InventoryTransfer inventoryTransfer;
    private GroupTransfer groupTransfer;

    @Override
    public void onEnable() {
        super.onEnable();
        if (!this.getServer().getPluginManager().isPluginEnabled("InvSeePlusPlus")){
            this.disable();
            return;
        }
        InvseePlusPlus invsee = (InvseePlusPlus) getServer().getPluginManager().getPlugin("InvSeePlusPlus");
        this.inventoryTransfer = new InventoryTransfer(this, invsee.getApi());
        this.groupTransfer = new GroupTransfer(getLogger());

        this.commandManager = new CommandManager(this);
        this.commandManager.init();
        this.commandManager.registerCommand(new TransferUserCommand(this));
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.commandManager.unregisterCommands();
    }

    public InventoryTransfer getInventoryTransfer() {
        return this.inventoryTransfer;
    }

    public GroupTransfer getGroupTransfer() {
        return this.groupTransfer;
    }
}
