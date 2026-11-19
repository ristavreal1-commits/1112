import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
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

    private boolean isProtectionStoneBlock(Block block, ApplicableRegionSet regions) {
        String blockName = block.getType().name();
        for (ProtectedRegion region : regions) {
            if (region.getId().startsWith("ps")) {
                Object matFlag = region.getFlags().entrySet().stream()
                        .filter(e -> e.getKey().getName().equals("ps-block-material"))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);

                if (matFlag != null && matFlag.toString().equalsIgnoreCase(blockName)) {
                    String coords = "ps" + block.getX() + "x" + block.getY() + "y" + block.getZ() + "z";
                    if (region.getId().equalsIgnoreCase(coords)) {
                        return true;
                    }
                }
            }
        }
        return false;
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
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(loc));
            StateFlag.State state = regions.queryState(null, targetFlag);

            if (state == StateFlag.State.ALLOW && !isProtectionStoneBlock(block, regions)) {
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
            Location loc = event.getBlock().getLocation();
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(loc));
            StateFlag.State state = regions.queryState(null, Flags.WITHER_DAMAGE);

            if (state == StateFlag.State.ALLOW && !isProtectionStoneBlock(event.getBlock(), regions)) {
                event.setCancelled(false);
            }
        }
    }
}
