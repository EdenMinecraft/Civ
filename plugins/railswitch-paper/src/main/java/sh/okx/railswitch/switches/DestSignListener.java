package sh.okx.railswitch.switches;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import sh.okx.railswitch.settings.SettingsManager;

public final class DestSignListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        //detect if is a dest sign
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        Block block = event.getClickedBlock();
        if (!(block.getState() instanceof Sign sign)) {
            return;
        }
        var side = sign.getSide(Side.FRONT);
        String line0 = PLAIN.serialize(side.line(0)).trim();
        if (SwitchType.find(line0) == null) {
            return;
        }

        //if so collect dest(s)
        StringBuilder dest = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            String line = PLAIN.serialize(side.line(i)).trim();
            if (!line.isEmpty()) {
                if (!dest.isEmpty()) dest.append(" ");
                dest.append(line);
            }
        }

        //stop sign opening
        event.setCancelled(true);

        //set the dest(s) — null clears it, matching "/dest"
        String destination = dest.toString();
        SettingsManager.setDestination(player, destination.isEmpty() ? null : destination);
    }
}
