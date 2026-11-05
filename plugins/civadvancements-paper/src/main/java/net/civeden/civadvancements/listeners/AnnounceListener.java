package net.civeden.civadvancements.listeners;

import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.Locale;
import net.civeden.civadvancements.CivAdvancements;
import net.civeden.civadvancements.CivAdvancementsConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import vg.civcraft.mc.civchat2.CivChat2;
import vg.civcraft.mc.civmodcore.ui.Bedrock;
import vg.civcraft.mc.civmodcore.ui.CivText;

/** Replaces vanilla's "has made the advancement" line with the configured layout. */
public class AnnounceListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    @EventHandler(priority = EventPriority.HIGH)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        CivAdvancementsConfig config = CivAdvancements.getInstance().getCivConfig();
        // Null already: the advancement doesn't announce, or the gamerule is off
        if (event.message() == null || !config.isAnnounceEnabled()) {
            return;
        }
        AdvancementDisplay display = event.getAdvancement().getDisplay();
        if (display == null) {
            return;
        }
        String template = config.getAnnounceFormat(display.frame().name().toLowerCase(Locale.ROOT));
        if (template == null || template.isEmpty()) {
            event.message(null);
            return;
        }
        Component title = display.title().colorIfAbsent(CivText.VALUE);
        Component hover = Component.text()
            .append(title)
            .append(Component.newline())
            .append(display.description().colorIfAbsent(CivText.INFO))
            .build();
        Component message = Component.text().color(CivText.INFO)
            .append(MM.deserialize(template,
                Placeholder.component("player", name(event.getPlayer())),
                Placeholder.component("advancement", title.hoverEvent(HoverEvent.showText(hover)))))
            .build();
        // Sent per player instead of through the event, so Bedrock players get it without the head
        event.message(null);
        boolean civChat = Bukkit.getPluginManager().isPluginEnabled("CivChat2");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(civChat
                ? CivChat2.getInstance().getCivChat2Manager().adapt(player, message)
                : Bedrock.adapt(player, message));
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    /** Same name block as chat and join lines - rank stars and name colour - when CivChat2 is there. */
    private static Component name(Player player) {
        if (Bukkit.getPluginManager().isPluginEnabled("CivChat2")) {
            return CivChat2.getInstance().getCivChat2Manager().identityTag(player);
        }
        return CivText.subject(player.getName());
    }
}
