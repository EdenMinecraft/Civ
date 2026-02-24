package com.github.longboyy.repelshitters.listeners;

import com.github.longboyy.repelshitters.RepelShitters;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

public class MobListener implements Listener {

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Exclude players
        if (entity instanceof Player) return;
        if(entity.getType() == EntityType.ARMOR_STAND) return;

        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;

        // Remove armor and weapons from drops
        event.getDrops().removeIf(item -> isArmor(item.getType()) || isWeapon(item.getType()));

        // Process items
        processItem(equipment.getItemInMainHand(), event);
        processItem(equipment.getItemInOffHand(), event);

        // Process each armor piece and add resource drops
        processItem(equipment.getHelmet(), event);
        processItem(equipment.getChestplate(), event);
        processItem(equipment.getLeggings(), event);
        processItem(equipment.getBoots(), event);
    }

    private boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") ||
            name.endsWith("_CHESTPLATE") ||
            name.endsWith("_LEGGINGS") ||
            name.endsWith("_BOOTS");
    }

    private boolean isWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") ||
            name.endsWith("_AXE") ||
            name.endsWith("_PICKAXE") ||
            name.endsWith("_SHOVEL") ||
            name.endsWith("_HOE") ||
            material == Material.BOW ||
            material == Material.CROSSBOW ||
            material == Material.TRIDENT;
    }

    private void processItem(ItemStack item, EntityDeathEvent event) {
        if (item == null) return;

        Material resourceType = getItemResource(item.getType());
        if (resourceType == null) return;

        // Drop 1-2 of the resource
        int amount = 1 + RepelShitters.RANDOM.nextInt(2); // Random between 1 and 2
        ItemStack drop = new ItemStack(resourceType, amount);
        event.getDrops().add(drop);
    }

    private Material getItemResource(Material itemType) {
        String typeName = itemType.name();

        if (typeName.startsWith("LEATHER_")) {
            return Material.LEATHER;
        } else if (typeName.startsWith("CHAINMAIL_")) {
            return Material.IRON_INGOT;
        } else if (typeName.startsWith("IRON_")) {
            return Material.IRON_INGOT;
        } else if (typeName.startsWith("GOLDEN_")) {
            return Material.GOLD_INGOT;
        } else if (typeName.startsWith("DIAMOND_")) {
            return Material.DIAMOND;
        } else if (typeName.startsWith("NETHERITE_")) {
            return Material.NETHERITE_SCRAP;
        }else if(itemType == Material.BOW || itemType == Material.CROSSBOW){
            return Material.STRING;
        }

        return null;
    }

}
