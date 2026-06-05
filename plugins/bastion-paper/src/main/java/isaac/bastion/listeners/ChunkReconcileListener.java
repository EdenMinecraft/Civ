package isaac.bastion.listeners;

import isaac.bastion.Bastion;
import isaac.bastion.BastionBlock;
import isaac.bastion.BastionType;
import isaac.bastion.storage.BastionBlockStorage;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Heals bastions whose stored type drifted from their world block, as their chunks load. The legacy
 * "old bastion" migration defaulted every pre-existing row to the first configured type
 * (citybastion) without converting the physical blocks, so old sponge bastions are recorded as
 * 50-radius city bastions. The block wins: such a row is retyped to the type whose material matches.
 *
 * <p>Cheap by construction -- it only inspects the bastions the index already knows are in the chunk
 * (one block each), never scanning for blocks. The retype is in place with an async-persisted DB
 * write (see {@link BastionBlockStorage#retypeBastion}).
 */
public class ChunkReconcileListener implements Listener {

    private final BastionBlockStorage storage;

    public ChunkReconcileListener(BastionBlockStorage storage) {
        this.storage = storage;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        int retyped = 0;
        for (BastionBlock bastion : storage.getBastionsInChunk(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            Location loc = bastion.getLocation();
            Material raw = chunk.getBlock(loc.getBlockX() & 15, loc.getBlockY(), loc.getBlockZ() & 15).getType();
            // A sponge bastion next to water reads back as WET_SPONGE; treat it as its dry type.
            BastionType want = BastionType.getByMaterial(raw == Material.WET_SPONGE ? Material.SPONGE : raw);
            if (want != null && !want.equals(bastion.getType())) {
                storage.retypeBastion(bastion, want);
                retyped++;
            }
        }
        if (retyped > 0) {
            Bastion.getPlugin().getLogger().log(Level.INFO,
                "Reconciled {0} bastion(s) to match their block in chunk {1},{2}",
                new Object[] {retyped, chunk.getX(), chunk.getZ()});
        }
    }
}
