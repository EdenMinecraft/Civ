package com.github.longboyy.repelshitters.listeners;

import com.github.longboyy.repelshitters.ActivityManager;
import com.github.longboyy.repelshitters.RepelShitters;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HappyGhast;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import vg.civcraft.mc.civmodcore.inventory.items.ItemMap;

public class PlayerListener implements Listener {

    private final RepelShitters plugin;

    private static final ItemStack SPLASH_POTION_HEALTH_2 = new ItemStack(Material.SPLASH_POTION, 1);

    static {
        PotionMeta meta = (PotionMeta) SPLASH_POTION_HEALTH_2.getItemMeta();
        meta.setBasePotionType(PotionType.STRONG_HEALING);
        SPLASH_POTION_HEALTH_2.setItemMeta(meta);
    }

    public PlayerListener(RepelShitters plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onStatIncrement(PlayerStatisticIncrementEvent event){
        if(event.getStatistic() != Statistic.PLAY_ONE_MINUTE){
            return;
        }

        if(!this.plugin.getActivityManager().isActive(event.getPlayer())){
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event){
        if(!event.hasChangedBlock()){
            return;
        }

        this.plugin.getActivityManager().trackActivity(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        this.plugin.getActivityManager().trackActivity(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        this.plugin.getActivityManager().trackActivity(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        this.plugin.getActivityManager().trackActivity(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        this.plugin.getActivityManager().stopTracking(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageByEntityEvent event){
        if(event.getDamager().getType() != EntityType.PLAYER && event.getEntity().getType() != EntityType.PLAYER) return;

        this.plugin.getAutoPotManager().handleAutoPot(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerHitPlayer(EntityDamageByEntityEvent event){
        if(event.getDamager().getType() != EntityType.PLAYER && event.getEntity().getType() != EntityType.PLAYER) return;

        if(event.getEntity() instanceof Player damager){
            event.setDamage(event.getDamage() / this.calculateScalar(damager));
        }


    }

    private float calculateScalar(Player player){
        double initialScalar = plugin.getConfigManager().getDamageConfig().startingScalar();
        double gameTimeWeighting = plugin.getConfigManager().getDamageConfig().gameTimeWeight();
        double joinTimeWeighting = plugin.getConfigManager().getDamageConfig().firstTimeWeight();

        long gameTimeThreshold = plugin.getConfigManager().getDamageConfig().gameTimeThreshold();
        long joinTimeThreshold = plugin.getConfigManager().getDamageConfig().firstJoinThreshold();

        return RepelShitters.calculateScalar(player, initialScalar, gameTimeWeighting, joinTimeWeighting, gameTimeThreshold, joinTimeThreshold);
    }
}
