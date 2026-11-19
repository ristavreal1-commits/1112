import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class CreeperWitherFix extends JavaPlugin implements Listener {

    private final Map<UUID, List<Block>> savedBlocks = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("CreeperWitherFix успешно загружен!");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onExplodePre(EntityExplodeEvent event) {
        EntityType type = event.getEntityType();
        if (type == EntityType.CREEPER || type == EntityType.WITHER || type == EntityType.WITHER_SKULL) {
            savedBlocks.put(event.getEntity().getUniqueId(), new ArrayList<>(event.blockList()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onExplodePost(EntityExplodeEvent event) {
        EntityType type = event.getEntityType();
        if (type != EntityType.CREEPER && type != EntityType.WITHER && type != EntityType.WITHER_SKULL) return;

        UUID id = event.getEntity().getUniqueId();
        List<Block> blocks = savedBlocks.remove(id);
        if (blocks == null || blocks.isEmpty()) return;

        StateFlag targetFlag = (type == EntityType.CREEPER) ? Flags.CREEPER_EXPLOSION : Flags.WITHER_DAMAGE;
        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        List<Block> allowedBlocks = new ArrayList<>();

        for (Block block : blocks) {
            Location loc = block.getLocation();
            StateFlag.State state = query.queryState(BukkitAdapter.adapt(loc), null, targetFlag);
            if (state == StateFlag.State.ALLOW) {
                allowedBlocks.add(block);
            }
        }

        if (!allowedBlocks.isEmpty()) {
            event.setCancelled(false);
            event.blockList().clear();
            event.blockList().addAll(allowedBlocks);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onWitherEatBlocks(EntityChangeBlockEvent event) {
        if (event.getEntityType() == EntityType.WITHER) {
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            StateFlag.State state = query.queryState(BukkitAdapter.adapt(event.getBlock().getLocation()), null, Flags.WITHER_DAMAGE);
            if (state == StateFlag.State.ALLOW) {
                event.setCancelled(false);
            }
        }
    }
}
