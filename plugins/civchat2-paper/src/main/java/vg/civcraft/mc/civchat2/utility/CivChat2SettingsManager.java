package vg.civcraft.mc.civchat2.utility;

import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import vg.civcraft.mc.civchat2.CivChat2;
import vg.civcraft.mc.civmodcore.players.settings.PlayerSettingAPI;
import vg.civcraft.mc.civmodcore.players.settings.gui.MenuSection;
import vg.civcraft.mc.civmodcore.players.settings.impl.BooleanSetting;
import vg.civcraft.mc.civmodcore.players.settings.impl.DisplayLocationSetting;
import vg.civcraft.mc.civmodcore.players.settings.impl.EnumSetting;
import vg.civcraft.mc.civmodcore.players.settings.impl.LongSetting;

public class CivChat2SettingsManager {

    private BooleanSetting showJoins;
    private BooleanSetting showLeaves;
    private BooleanSetting sendOwnKills;
    private BooleanSetting receiveKills;
    private BooleanSetting receiveKillsFromIgnoredPlayers;
    private BooleanSetting showChatGroup;
    private DisplayLocationSetting chatGroupLocation;
    private LongSetting chatUnmuteTimer;
    private EnumSetting<KillMessageFormat> killMessageFormat;
    private BooleanSetting showAFKStatus;
    private BooleanSetting showPrefix;
    private DisplayLocationSetting afkStatusLocation;
    private BooleanSetting showGroupColors;
    private BooleanSetting showPlayerHeads;
    private BooleanSetting messageSound;
    private EnumSetting<PrivateMessageColor> privateMessageColor;

    public CivChat2SettingsManager() {
        initSettings();
    }

    private void initSettings() {
        MenuSection menu = PlayerSettingAPI.getMainMenu().createMenuSection("CivChat",
            "All options related to CivChat.", new ItemStack(Material.OAK_SIGN));

        showJoins = new BooleanSetting(CivChat2.getInstance(), true, "Show Player Joins", "showJoins",
            "Should player join messages be shown?");
        PlayerSettingAPI.registerSetting(showJoins, menu);

        showLeaves = new BooleanSetting(CivChat2.getInstance(), true, "Show Players Leaving", "showLeaves",
            "Should player leave messages be shown?");
        PlayerSettingAPI.registerSetting(showLeaves, menu);

        sendOwnKills = new BooleanSetting(CivChat2.getInstance(), true, "Broadcast your kills", "civChatBroadcastKills",
            "Should kills you make be broadcasted to nearby players?");
        PlayerSettingAPI.registerSetting(sendOwnKills, menu);

        receiveKills = new BooleanSetting(CivChat2.getInstance(), true, "Receive kill broadcasts",
            "civChatReceiveKills", "Do you want to receive broadcasts for nearby kills");
        PlayerSettingAPI.registerSetting(receiveKills, menu);

        receiveKillsFromIgnoredPlayers = new BooleanSetting(CivChat2.getInstance(), false,
            "Receive kill broadcasts from ignored players", "civChatReceiveKillsIgnored",
            "Do you want to receive kill broadcasts from killers you have ignored");
        PlayerSettingAPI.registerSetting(receiveKillsFromIgnoredPlayers, menu);

        showChatGroup = new BooleanSetting(CivChat2.getInstance(), true, "Show current chat group", "showChatGroup",
            "Should player chat group be shown?");
        PlayerSettingAPI.registerSetting(showChatGroup, menu);

        chatGroupLocation = new DisplayLocationSetting(CivChat2.getInstance(), DisplayLocationSetting.DisplayLocation.SIDEBAR,
            "Chat Group Location", "chatGroupLocation", new ItemStack(Material.ARROW), "the current chat group");
        PlayerSettingAPI.registerSetting(chatGroupLocation, menu);

        chatUnmuteTimer = new LongSetting(CivChat2.getInstance(), 0L, "Global chat mute", "chatGlobalMuteTimer");
        PlayerSettingAPI.registerSetting(chatUnmuteTimer, null);

        killMessageFormat = new EnumSetting<>(CivChat2.getInstance(), KillMessageFormat.WITH, "Kill Message Format", "killMessageFormat", new ItemStack(Material.WRITABLE_BOOK), "Choose your kill message format", true, KillMessageFormat.class);
        PlayerSettingAPI.registerSetting(killMessageFormat, menu);

        afkStatusLocation = new DisplayLocationSetting(CivChat2.getInstance(), DisplayLocationSetting.DisplayLocation.SIDEBAR,
            "AFK Status Location", "afkStatusLocation", new ItemStack(Material.ARROW), "the AFK status");
        PlayerSettingAPI.registerSetting(afkStatusLocation, menu);

        showPrefix = new BooleanSetting(CivChat2.getInstance(), true, "Show vanity prefix", "showPrefix",
            "Should the star prefix be shown?");
        PlayerSettingAPI.registerSetting(showPrefix, menu);
        showGroupColors = new BooleanSetting(CivChat2.getInstance(), true, "Show group colors in chat", "showGroupColors",
            "Disabling this setting will make all group colors in chat, gray");
        PlayerSettingAPI.registerSetting(showGroupColors, menu);

        showPlayerHeads = new BooleanSetting(CivChat2.getInstance(), true, "Show player heads in chat", "showPlayerHeads",
            "Should player faces be shown next to names in chat?");
        PlayerSettingAPI.registerSetting(showPlayerHeads, menu);

        messageSound = new BooleanSetting(CivChat2.getInstance(), true, "Private message sound", "privateMessageSound",
            "Should a sound play when you receive a private message?");
        PlayerSettingAPI.registerSetting(messageSound, menu);

        privateMessageColor = new EnumSetting<>(CivChat2.getInstance(), PrivateMessageColor.LIGHT_PURPLE,
            "Private message color", "privateMessageColor", new ItemStack(Material.PINK_DYE),
            "The color of message text in /msg and /r", true, PrivateMessageColor.class);
        PlayerSettingAPI.registerSetting(privateMessageColor, menu);
    }

    public LongSetting getGlobalChatMuteSetting() {
        return chatUnmuteTimer;
    }

    public boolean getShowJoins(UUID uuid) {
        return showJoins.getValue(uuid);
    }

    public boolean getShowLeaves(UUID uuid) {
        return showLeaves.getValue(uuid);
    }

    public boolean getSendOwnKills(UUID uuid) {
        return sendOwnKills.getValue(uuid);
    }

    public boolean getReceiveKills(UUID uuid) {
        return receiveKills.getValue(uuid);
    }

    public boolean getReceiveKillsFromIgnored(UUID uuid) {
        return receiveKillsFromIgnoredPlayers.getValue(uuid);
    }

    public boolean getShowChatGroup(UUID uuid) {
        return showChatGroup.getValue(uuid);
    }

    public DisplayLocationSetting getChatGroupLocation() {
        return chatGroupLocation;
    }

    public KillMessageFormat getKillMessageFormat(UUID uuid) {
        return killMessageFormat.getValue(uuid);
    }

    public boolean getShowAFKStatus(UUID uuid) {return showAFKStatus.getValue(uuid); }

    public boolean isShowPrefix(UUID uuid) {
        return showPrefix.getValue(uuid);
    }

    public boolean showGroupColors(UUID uuid) {
        return showGroupColors.getValue(uuid);
    }

    public boolean showPlayerHeads(UUID uuid) {
        return showPlayerHeads.getValue(uuid);
    }

    public DisplayLocationSetting getAfkStatusLocation() { return afkStatusLocation; }

    public boolean getMessageSound(UUID uuid) {
        return messageSound.getValue(uuid);
    }

    public TextColor getPrivateMessageColor(UUID uuid) {
        return privateMessageColor.getValue(uuid).color;
    }

    public enum PrivateMessageColor {
        LIGHT_PURPLE(NamedTextColor.LIGHT_PURPLE),
        DARK_PURPLE(NamedTextColor.DARK_PURPLE),
        LAVENDER(TextColor.color(0xC88FD9)),
        PALE_LAVENDER(TextColor.color(0xD8B4E2)),
        PINK(TextColor.color(0xF5A9D0)),
        RED(NamedTextColor.RED),
        DARK_RED(NamedTextColor.DARK_RED),
        ORANGE(TextColor.color(0xFFA040)),
        GOLD(NamedTextColor.GOLD),
        YELLOW(NamedTextColor.YELLOW),
        GREEN(NamedTextColor.GREEN),
        DARK_GREEN(NamedTextColor.DARK_GREEN),
        MINT(TextColor.color(0x9EF0C0)),
        AQUA(NamedTextColor.AQUA),
        DARK_AQUA(NamedTextColor.DARK_AQUA),
        LIGHT_BLUE(TextColor.color(0x8CC8FF)),
        BLUE(NamedTextColor.BLUE),
        DARK_BLUE(NamedTextColor.DARK_BLUE),
        WHITE(NamedTextColor.WHITE),
        GRAY(NamedTextColor.GRAY),
        DARK_GRAY(NamedTextColor.DARK_GRAY);

        public final TextColor color;

        PrivateMessageColor(TextColor color) {
            this.color = color;
        }
    }

    public enum KillMessageFormat {
        FOR(
            "for"
        ),
        WHILE(
            "while"
        ),
        BLANK(
            ""
        ),
        USING(
            "using"
        ),
        BY(
            "by"
        ),
        WITH(
            "with"
        );

        public final String simpleDescription;

        private KillMessageFormat(String simpleDescription) {
            this.simpleDescription = simpleDescription;
        }
    }
}
