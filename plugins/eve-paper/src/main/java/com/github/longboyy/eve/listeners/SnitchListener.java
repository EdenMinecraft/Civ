package com.github.longboyy.eve.listeners;

import com.github.longboyy.eve.EvePlugin;
import com.github.longboyy.eve.model.SnitchHitType;
import com.untamedears.itemexchange.glues.jukealert.ShopPurchaseSnitchEvent;
import com.untamedears.jukealert.events.PlayerHitSnitchEvent;
import com.untamedears.jukealert.events.PlayerLoginSnitchEvent;
import com.untamedears.jukealert.events.PlayerLogoutSnitchEvent;
import com.untamedears.jukealert.model.Snitch;
import com.untamedears.jukealert.util.JukeAlertPermissionHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import vg.civcraft.mc.namelayer.NameLayerAPI;

public class SnitchListener implements Listener {
    private final EvePlugin plugin;

    public SnitchListener(EvePlugin plugin){
        this.plugin = plugin;
    }

    @EventHandler
    public void onSnitchHit(PlayerHitSnitchEvent event){
        // player hit snitch, send to discord
        if(isSnitchImmune(event.getPlayer(), event.getSnitch())) return;
        this.plugin.getRelayManager().publishSnitchHit(event.getPlayer(), event.getSnitch(), SnitchHitType.ENTER);
    }

    @EventHandler
    public void onSnitchLogin(PlayerLoginSnitchEvent event){
        if(isSnitchImmune(event.getPlayer(), event.getSnitch())) return;
        this.plugin.getRelayManager().publishSnitchHit(event.getPlayer(), event.getSnitch(), SnitchHitType.LOGIN);

    }

    @EventHandler
    public void onSnitchLogout(PlayerLogoutSnitchEvent event){
        if(isSnitchImmune(event.getPlayer(), event.getSnitch())) return;
        this.plugin.getRelayManager().publishSnitchHit(event.getPlayer(), event.getSnitch(), SnitchHitType.LOGOUT);
    }

    @EventHandler
    public void onSnitchPurchase(ShopPurchaseSnitchEvent event){
        if(isSnitchImmune(event.getPurchaser(), event.getSnitch())) return;
        this.plugin.getRelayManager().publishPurchase(event.getPurchaser(), event.getSnitch(), event.getTrade().getBlock().getLocation(), event.getPaymentItems(), event.getPurchasedItems());
    }

    private boolean isSnitchImmune(Player player, Snitch snitch){
        return NameLayerAPI.getGroupManager().hasAccess(snitch.getGroup(), player.getUniqueId(), JukeAlertPermissionHandler.getSnitchImmune());
    }
}
