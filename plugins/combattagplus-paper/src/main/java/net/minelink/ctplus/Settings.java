package net.minelink.ctplus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import vg.civcraft.mc.civmodcore.players.settings.AdminSetting;
import vg.civcraft.mc.civmodcore.players.settings.AdminSettingAPI;
import vg.civcraft.mc.civmodcore.players.settings.PlayerSettingAPI;
import vg.civcraft.mc.civmodcore.players.settings.gui.AdminMenuSection;
import vg.civcraft.mc.civmodcore.players.settings.gui.MenuSection;
import vg.civcraft.mc.civmodcore.players.settings.impl.BooleanSetting;
import vg.civcraft.mc.civmodcore.players.settings.impl.IntegerSetting;

import static java.util.concurrent.TimeUnit.*;

public final class Settings {

    private final CombatTagPlus plugin;

    // Gameplay values admins can change live through /adminconfig, the rest is only read from the config file
    private BooleanSetting logoutSound;

    private AdminSetting<Integer> tagDuration;
    private AdminSetting<Integer> logoutWaitTime;
    private AdminSetting<Integer> npcDespawnTime;
    private AdminSetting<Boolean> resetDespawnTimeOnHit;
    private AdminSetting<Boolean> mobTagging;
    private AdminSetting<Boolean> onlyTagAttacker;
    private AdminSetting<Boolean> disableSelfTagging;
    private AdminSetting<Boolean> disableCreativeTags;
    private AdminSetting<Boolean> resetTagOnPearl;
    private AdminSetting<Boolean> untagOnKick;
    private AdminSetting<Boolean> untagOnPluginTeleport;
    private AdminSetting<Boolean> alwaysSpawn;
    private AdminSetting<Boolean> instantlyKill;
    private AdminSetting<Boolean> playEffect;
    private AdminSetting<Boolean> disableBlockEdit;
    private AdminSetting<Boolean> disableStorageAccess;
    private AdminSetting<Boolean> disableEnderpearls;
    private AdminSetting<Boolean> disableFlying;
    private AdminSetting<Boolean> disableTeleportation;
    private AdminSetting<Boolean> disableCrafting;
    private AdminSetting<Boolean> denySafezone;
    private AdminSetting<Boolean> denySafezoneEnderpearl;
    private AdminSetting<Boolean> forceFields;
    private AdminSetting<Integer> forceFieldRadius;

    Settings(CombatTagPlus plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        Configuration defaults = plugin.getConfig().getDefaults();
        defaults.set("disabled-worlds", new ArrayList<>());
        defaults.set("command-blacklist", new ArrayList<>());
        defaults.set("command-whitelist", new ArrayList<>());
    }

    public void registerPlayerSettings() {
        MenuSection menu = PlayerSettingAPI.getMainMenu().createMenuSection("Combat Tag",
            "Options related to combat tagging and safe logout.", new ItemStack(Material.IRON_SWORD));

        logoutSound = new BooleanSetting(plugin, true, "Safe logout sound", "ctplusLogoutSound",
            "Should the /logout countdown play a sound each second?");
        PlayerSettingAPI.registerSetting(logoutSound, menu);
    }

    public boolean playLogoutSound(UUID player) {
        return logoutSound == null || logoutSound.getValue(player);
    }

    /**
     * Binds the gameplay values to admin settings. Call once the config file is up to date, registering again
     * replaces the previous settings
     */
    public void registerAdminSettings() {
        AdminSettingAPI.setPluginIcon(plugin, new ItemStack(Material.IRON_SWORD));
        AdminMenuSection menu = AdminSettingAPI.getPluginMenu(plugin);

        AdminMenuSection timers = menu.getSubsection("Timers", "How long tags, safe logouts and NPCs last",
            new ItemStack(Material.CLOCK));
        tagDuration = integer(timers, "tag-duration", "Tag duration", 15, Material.CLOCK,
            "Seconds both players stay tagged after a hit");
        logoutWaitTime = integer(timers, "logout-wait-time", "Logout wait time", 10, Material.OAK_DOOR,
            "Seconds a player must stand still to /logout safely");
        npcDespawnTime = integer(timers, "npc-despawn-time", "NPC despawn time", 60, Material.ZOMBIE_HEAD,
            "Seconds a combat logger's NPC stays if it isn't killed");
        resetDespawnTimeOnHit = toggle(timers, "reset-despawn-time-on-hit", "Reset NPC despawn on hit", true,
            "Restart the NPC despawn time whenever it gets hit");

        AdminMenuSection tagging = menu.getSubsection("Tagging", "Who gets tagged and what happens on logging",
            new ItemStack(Material.NAME_TAG));
        mobTagging = toggle(tagging, "mob-tagging", "Mob tagging", false, "Mobs tag the players they hit");
        onlyTagAttacker = toggle(tagging, "only-tag-attacker", "Only tag attacker", false,
            "Only the attacker gets tagged, not the victim");
        disableSelfTagging = toggle(tagging, "disable-self-tagging", "Disable self tagging", true,
            "Players can't tag themselves");
        disableCreativeTags = toggle(tagging, "disable-creative-tags", "Disable creative tags", true,
            "Players in creative don't get tagged");
        resetTagOnPearl = toggle(tagging, "reset-tag-on-pearl", "Reset tag on pearl", false,
            "Throwing an ender pearl restarts the tag");
        untagOnKick = toggle(tagging, "untag-on-kick", "Untag on kick", false,
            "Untag players who get kicked instead of spawning an NPC");
        untagOnPluginTeleport = toggle(tagging, "untag-on-plugin-teleport", "Untag on plugin teleport", false,
            "Untag players teleported by plugins");
        alwaysSpawn = toggle(tagging, "always-spawn", "Always spawn NPC", false,
            "Spawn an NPC on every logout, tagged or not");
        instantlyKill = toggle(tagging, "instantly-kill", "Instantly kill", false,
            "Kill combat loggers instead of spawning an NPC");
        playEffect = toggle(tagging, "play-effect", "Play effect", true, "Play an effect when someone combat logs");

        AdminMenuSection restrictions = menu.getSubsection("Restrictions", "What tagged players can't do",
            new ItemStack(Material.BARRIER));
        disableBlockEdit = toggle(restrictions, "disable-block-edit", "Disable block editing", false,
            "Tagged players can't place or break blocks");
        disableStorageAccess = toggle(restrictions, "disable-storage-access", "Disable storage access", false,
            "Tagged players can't open chests and other storage");
        disableEnderpearls = toggle(restrictions, "disable-enderpearls", "Disable ender pearls", false,
            "Tagged players can't throw ender pearls");
        disableFlying = toggle(restrictions, "disable-flying", "Disable flying", false, "Tagged players can't fly");
        disableTeleportation = toggle(restrictions, "disable-teleportation", "Disable teleportation", false,
            "Tagged players can't teleport");
        disableCrafting = toggle(restrictions, "disable-crafting", "Disable crafting", false,
            "Tagged players can't craft");

        AdminMenuSection safezones = menu.getSubsection("Safezones", "Keeping tagged players out of PvP-free areas",
            new ItemStack(Material.SHIELD));
        denySafezone = toggle(safezones, "deny-safezone", "Deny safezones", true,
            "Push tagged players back out of PvP-free areas");
        denySafezoneEnderpearl = toggle(safezones, "deny-safezone-enderpearl", "Deny safezone pearls", true,
            "Tagged players can't pearl into PvP-free areas");
        forceFields = toggle(safezones, "force-fields", "Force fields", true,
            "Show fake walls around PvP-free areas to tagged players");
        forceFieldRadius = integer(safezones, "force-field-radius", "Force field radius", 10, Material.GLASS,
            "Blocks around tagged players checked for force fields, higher is laggier");
    }

    private AdminSetting<Integer> integer(AdminMenuSection menu, String path, String name, int defaultValue,
                                          Material icon, String description) {
        return AdminSettingAPI.registerSetting(new IntegerSetting(plugin, defaultValue, name, "ctplus." + path,
            new ItemStack(icon), description, false), path, menu);
    }

    private AdminSetting<Boolean> toggle(AdminMenuSection menu, String path, String name, boolean defaultValue,
                                         String description) {
        return AdminSettingAPI.registerSetting(new BooleanSetting(plugin, defaultValue, name, "ctplus." + path,
            description), path, menu);
    }

    public void update() {
        // Initialize the new config cache
        List<Map<String, Object>> config = new ArrayList<>();

        // Default config path
        Path path = Paths.get(plugin.getDataFolder().getAbsolutePath() + File.separator + "config.yml");

        // Iterate through new config
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("config.yml")));
        for (String key : defaultConfig.getKeys(true)) {
            // Convert key to correct format for a single line
            String oneLineKey = StringUtils.repeat("  ", key.split(".").length) + key + ": ";

            // Generate new config section
            Map<String, Object> section = new HashMap<>();
            section.put("key", oneLineKey);

            // Attempt to save value from old configuration
            if (key.equals("config-version")) {
                section.put("value", defaultConfig.get(key));
            } else if (plugin.getConfig().get(key) != null) {
                section.put("value", plugin.getConfig().get(key));
            } else {
                section.put("value", defaultConfig.get(key));
            }

            // Save section to cache
            config.add(section);
        }

        // Attempt to open the default config
        try (BufferedReader br = new BufferedReader(new InputStreamReader(plugin.getResource("config.yml")))) {
            // Iterate through all lines in this config
            String current;
            String previous = null;
            List<String> comments = new ArrayList<>();
            while ((current = br.readLine()) != null) {
                // If previous line is a comment add it, else clear the comments
                if (previous != null && previous.matches("(| +)#.*")) {
                    comments.add(previous);
                } else {
                    comments.clear();
                }

                // Iterate through current config cache
                for (Map<String, Object> section : config) {
                    // Do nothing if key is not valid
                    if (section.get("key") == null) continue;

                    // Do nothing if there are no comments to assign
                    if (comments.isEmpty()) continue;

                    // Do nothing if current line doesn't start with this key
                    String key = section.get("key").toString();
                    if (!current.startsWith(key.substring(0, key.length() - 1))) continue;

                    // Add comment to config cache
                    section.put("comments", new ArrayList<>(comments));
                }

                // Set the previous line
                previous = current;
            }
        } catch (IOException e) {
            // Failed to read from default config within plugin jar
            plugin.getLogger().severe("**CONFIG ERROR**");
            plugin.getLogger().severe("Failed to read from default config within plugin jar.");

            // Leave a stack trace in console
            e.printStackTrace();
        }

        // Attempt to open the new config
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            // Iterate through new cached config
            for (int i = 0; i < config.size(); i++) {
                // Get section of the config at this index
                Map<String, Object> section = config.get(i);

                // Do nothing if key is invalid
                if (section.get("key") == null) continue;

                // Do nothing if value is invalid
                if (section.get("value") == null) continue;

                // Write the comments if they are valid
                Object comments = section.get("comments");
                if (comments != null && comments instanceof List) {
                    for (Object o : (List) comments) {
                        writer.write(o.toString());
                        writer.newLine();
                    }
                }

                // Write the key
                String key = section.get("key").toString();
                writer.write(key);

                // Write the value
                Object value = section.get("value");

                if (value instanceof String) {
                    writer.write("'" + value.toString().replace("'", "''") + "'");
                } else if (value instanceof List) {
                    List list = (List) value;
                    int indent = key.length() - key.replace(" ", "").length() - 1;
                    for (Object s : list) {
                        writer.newLine();
                        writer.write(StringUtils.repeat(" ", indent) + "  - '" + s.toString().replace("'", "''") + "'");
                    }
                } else {
                    writer.write(value.toString());
                }

                // Write a couple more lines for extra space ;-)
                if (config.size() > i + 1) {
                    writer.newLine();
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            // Failed to write new config file to the disk
            plugin.getLogger().severe("**CONFIG ERROR**");
            plugin.getLogger().severe("Failed to write an updated config to the disk.");

            // Leave a stack trace in console
            e.printStackTrace();
        }

        // Reload the updated configuration
        plugin.reloadConfig();
        load();
    }

    public int getConfigVersion() {
        return plugin.getConfig().getInt("config-version", 0);
    }

    public int getLatestConfigVersion() {
        return plugin.getConfig().getDefaults().getInt("config-version", 0);
    }

    public boolean isOutdated() {
        return getConfigVersion() < getLatestConfigVersion();
    }

    public int getTagDuration() {
        return Math.max(0, tagDuration.getValue());
    }

    public String getTagMessage() {
        String message = plugin.getConfig().getString("tag-message", "");
        return message;
    }

    public String getTagUnknownMessage() {
        String message = plugin.getConfig().getString("tag-unknown-message", "");
        return message;
    }

    public String getUntagMessage() {
        String message = plugin.getConfig().getString("untag-message", "");
        return message;
    }

    public boolean resetTagOnPearl() {
        return resetTagOnPearl.getValue();
    }

    public boolean playEffect() {
        return playEffect.getValue();
    }

    public boolean alwaysSpawn() {
        return alwaysSpawn.getValue();
    }

    public boolean mobTagging() {
        return mobTagging.getValue();
    }

    public int getLogoutWaitTime() {
        return Math.max(0, logoutWaitTime.getValue());
    }

    public String getLogoutCancelledMessage() {
        String message = plugin.getConfig().getString("logout-cancelled-message", "");
        return message;
    }

    public String getLogoutSuccessMessage() {
        String message = plugin.getConfig().getString("logout-success-message", "");
        return message;
    }

    public String getLogoutPendingMessage() {
        String message = plugin.getConfig().getString("logout-pending-message", "");
        return message;
    }

    public boolean instantlyKill() {
        return instantlyKill.getValue();
    }

    public boolean untagOnKick() {
        return untagOnKick.getValue();
    }

    public List<String> getUntagOnKickBlacklist() {
        return plugin.getConfig().getStringList("untag-on-kick-blacklist");
    }

    public boolean onlyTagAttacker() {
        return onlyTagAttacker.getValue();
    }

    public boolean disableSelfTagging() {
        return disableSelfTagging.getValue();
    }

    public boolean disableBlockEdit() {
        return disableBlockEdit.getValue();
    }

    public String getDisableBlockEditMessage() {
        String message = plugin.getConfig().getString("disable-block-edit-message", "");
        return message;
    }

    public boolean disableStorageAccess() {
        return disableStorageAccess.getValue();
    }

    public String getDisableStorageAccessMessage() {
        String message = plugin.getConfig().getString("disable-storage-access-message", "");
        return message;
    }

    public boolean disableCreativeTags() {
        return disableCreativeTags.getValue();
    }

    public boolean disableEnderpearls() {
        return disableEnderpearls.getValue();
    }

    public String getDisableEnderpearlsMessage() {
        String message = plugin.getConfig().getString("disable-enderpearls-message", "");
        return message;
    }

    public boolean disableFlying() {
        return disableFlying.getValue();
    }

    public String getDisableFlyingMessage() {
        String message = plugin.getConfig().getString("disable-flying-message", "");
        return message;
    }

    public boolean disableTeleportation() {
        return disableTeleportation.getValue();
    }

    public String getDisableTeleportationMessage() {
        String message = plugin.getConfig().getString("disable-teleportation-message", "");
        return message;
    }

    public boolean disableCrafting() {
        return disableCrafting.getValue();
    }

    public String getDisableCraftingMessage() {
        String message = plugin.getConfig().getString("disable-crafting-message", "");
        return message;
    }

    public int getNpcDespawnTime() {
        return Math.max(0, npcDespawnTime.getValue());
    }

    public int getNpcDespawnMillis() {
        return getNpcDespawnTime() * 1000;
    }

    public boolean resetDespawnTimeOnHit() {
        return resetDespawnTimeOnHit.getValue();
    }

    public boolean generateRandomName() {
        return plugin.getConfig().getBoolean("generate-random-name");
    }

    public String getRandomNamePrefix() {
        return plugin.getConfig().getString("random-name-prefix");
    }

    public List<String> getKillMessages() {
        return plugin.getConfig().getStringList("kill-messages");
    }

    public List<String> getKillMessagesItem() {
        return plugin.getConfig().getStringList("kill-messages-item");
    }

    public boolean useBarApi() {
        return plugin.getConfig().getBoolean("barapi");
    }

    public String getBarApiEndedMessage() {
        String message = plugin.getConfig().getString("barapi-ended-message", "You're no longer in combat");
        return message;
    }

    public String getBarApiCountdownMessage() {
        String message = plugin.getConfig().getString("barapi-countdown-message", "In combat <dark_gray>-</dark_gray> <remaining>");
        return message;
    }

    public boolean denySafezone() {
        return denySafezone.getValue();
    }

    public boolean denySafezoneEnderpearl() {
        return denySafezoneEnderpearl.getValue();
    }

    public boolean useForceFields() {
        return forceFields.getValue();
    }

    public int getForceFieldRadius() {
        return Math.max(0, forceFieldRadius.getValue());
    }

    public String getForceFieldMaterial() {
        return plugin.getConfig().getString("force-field-material");
    }

    public byte getForceFieldMaterialDamage() {
        return (byte) plugin.getConfig().getInt("force-field-material-damage");
    }

    public boolean useFactions() {
        return plugin.getConfig().getBoolean("factions");
    }

    public boolean useWorldGuard() {
        return plugin.getConfig().getBoolean("worldguard");
    }

    public List<String> getDisabledWorlds() {
        return plugin.getConfig().getStringList("disabled-worlds");
    }

    public String getDisabledCommandMessage() {
        String message = plugin.getConfig().getString("disabled-command-message", "");
        return message;
    }

    public boolean isCommandBlacklisted(String message) {
        if (message.charAt(0) == '/') {
            message = message.substring(1);
        }

        message = message.toLowerCase();

        for (String command : plugin.getConfig().getStringList("command-whitelist")) {
            if (command.equals("*") || message.equals(command) || message.startsWith(command + " ")) {
                return false;
            }
        }

        for (String command : plugin.getConfig().getStringList("command-blacklist")) {
            if (command.equals("*") || message.equals(command) || message.startsWith(command + " ")) {
                return true;
            }
        }

        return false;
    }

    public boolean untagOnPluginTeleport() {
        return untagOnPluginTeleport.getValue();
    }

    public String getCommandUntagMessage() {
        return plugin.getConfig().getString("command-untag-message", "");
    }

    public String getCommandTagMessage() {
        return plugin.getConfig().getString("command-tag-message", "");
    }

    public String formatDuration(long seconds) {
        List<String> parts = new ArrayList<>();
        for (TimeUnit timeUnit : new TimeUnit[]{DAYS, HOURS, MINUTES, SECONDS}) {
            long duration = seconds / SECONDS.convert(1, timeUnit);
            if (duration > 0) {
                seconds -= SECONDS.convert(duration, timeUnit);
                String englishWord = timeUnit.name().toLowerCase(Locale.ENGLISH);
                String durationWord = plugin.getConfig().getString("duration-words." + englishWord, englishWord);
                parts.add(duration + " " + durationWord);
            }
        }
        String formatted = StringUtils.join(parts, ", ");
        if (formatted.contains(", ")) {
            int index = formatted.lastIndexOf(", ");
            StringBuilder builder = new StringBuilder(formatted);
            formatted = builder.replace(index, index + 2, " and ").toString();
        }

        return formatted;
    }
}
