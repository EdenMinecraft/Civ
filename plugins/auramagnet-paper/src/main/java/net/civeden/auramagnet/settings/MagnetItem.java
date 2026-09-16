package net.civeden.auramagnet.settings;

import org.bukkit.Material;

/**
 * The set of dropped item materials a Magnet Hoe can pull in, each individually
 * toggleable per-player via the MagnetHoe section in /config.
 */
public enum MagnetItem {

    WHEAT(Material.WHEAT, "Wheat"),
    WHEAT_SEEDS(Material.WHEAT_SEEDS, "Wheat Seeds"),
    POTATO(Material.POTATO, "Potato"),
    CARROT(Material.CARROT, "Carrot"),
    BEETROOT(Material.BEETROOT, "Beetroot"),
    BEETROOT_SEEDS(Material.BEETROOT_SEEDS, "Beetroot Seeds"),
    NETHER_WART(Material.NETHER_WART, "Nether Wart"),
    COCOA_BEANS(Material.COCOA_BEANS, "Cocoa Beans"),
    SUGAR_CANE(Material.SUGAR_CANE, "Sugar Cane"),
    BAMBOO(Material.BAMBOO, "Bamboo"),
    MELON(Material.MELON, "Melon"),
    MELON_SLICE(Material.MELON_SLICE, "Melon Slice"),
    PUMPKIN(Material.PUMPKIN, "Pumpkin"),
    SWEET_BERRIES(Material.SWEET_BERRIES, "Sweet Berries"),
    GLOW_BERRIES(Material.GLOW_BERRIES, "Glow Berries"),
    TORCHFLOWER_SEEDS(Material.TORCHFLOWER_SEEDS, "Torchflower Seeds"),
    PITCHER_POD(Material.PITCHER_POD, "Pitcher Pod"),
    SEA_PICKLE(Material.SEA_PICKLE, "Sea Pickle"),
    OAK_SAPLING(Material.OAK_SAPLING, "Oak Sapling"),
    SPRUCE_SAPLING(Material.SPRUCE_SAPLING, "Spruce Sapling"),
    BIRCH_SAPLING(Material.BIRCH_SAPLING, "Birch Sapling"),
    JUNGLE_SAPLING(Material.JUNGLE_SAPLING, "Jungle Sapling"),
    ACACIA_SAPLING(Material.ACACIA_SAPLING, "Acacia Sapling"),
    CHERRY_SAPLING(Material.CHERRY_SAPLING, "Cherry Sapling"),
    DARK_OAK_SAPLING(Material.DARK_OAK_SAPLING, "Dark Oak Sapling"),
    PALE_OAK_SAPLING(Material.PALE_OAK_SAPLING, "Pale Oak Sapling"),
    MANGROVE_PROPAGULE(Material.MANGROVE_PROPAGULE, "Mangrove Propagule");

    private final Material material;
    private final String niceName;

    MagnetItem(Material material, String niceName) {
        this.material = material;
        this.niceName = niceName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getNiceName() {
        return niceName;
    }
}
