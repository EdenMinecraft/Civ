package com.github.longboyy.citadelgeyserfix.listeners;

import com.github.longboyy.citadelgeyserfix.CitadelGeyserFix;
import com.github.longboyy.citadelgeyserfix.ExploitTracker;
import com.github.longboyy.citadelgeyserfix.PositionTracker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.geysermc.floodgate.api.FloodgateApi;
import vg.civcraft.mc.citadel.Citadel;
import vg.civcraft.mc.citadel.model.Reinforcement;
import vg.civcraft.mc.namelayer.permission.PermissionType;

public class PlayerListener implements Listener {

    private final CitadelGeyserFix plugin;
    private final FloodgateApi floodgateApi;

    public PlayerListener(CitadelGeyserFix plugin){
        this.plugin = plugin;
        this.floodgateApi = FloodgateApi.getInstance();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event){
        // We need floodgate to be able to do these checks
        if(this.floodgateApi == null) return;

        // if the event isn't already cancelled, we don't even care
        if(event.useInteractedBlock() != Event.Result.DENY) return;

        // We only care if the player interacted with a block
        if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        boolean isBedrockPlayer = floodgateApi.isFloodgatePlayer(player.getUniqueId());

        if(!isBedrockPlayer){
            return;
        }

        Block block = event.getClickedBlock();
        Material blockMaterial = block.getType();
        String blockMaterialName  = blockMaterial.toString();

        if(!blockMaterialName.contains("DOOR") || blockMaterial == Material.IRON_DOOR || blockMaterial == Material.IRON_TRAPDOOR){
            return;
        }

        Reinforcement rein = Citadel.getInstance().getReinforcementManager().getReinforcement(block);

        if(rein == null){
            return;
        }

        if(rein.hasPermission(player, PermissionType.getPermission("DOORS"))){
           return;
        }

        // track as exploit
        ExploitTracker.trackExploit(player, block);
        PositionTracker.teleportBack(player);
    }
    @EventHandler()
    public void onPlayerBreakDoor(){

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        PositionTracker.updatePosition(event.getPlayer());
    }
}
