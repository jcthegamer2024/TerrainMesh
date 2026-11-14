package org.sylvarion.terrainmesh.integration;

import com.sk89q.worldedit.*;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.*;
import org.bukkit.entity.Player;
import org.sylvarion.terrainmesh.config.ConfigManager;
import org.sylvarion.terrainmesh.mesh.TerrainHeightmap;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

public final class FAWEBridge {
    /** Capture Bukkit-backed actor and session on the server thread. */
    public record Context(com.sk89q.worldedit.entity.Player actor,LocalSession history,com.sk89q.worldedit.world.World world) {}
    public Context context(Player player) {
        var actor=BukkitAdapter.adapt(player);
        if(actor.getLimit().MAX_HISTORY==0) throw new IllegalArgumentException("FAWE history is disabled for your account. Enable undo history before generating terrain.");
        return new Context(actor,WorldEdit.getInstance().getSessionManager().get(actor),BukkitAdapter.adapt(player.getWorld()));
    }
    public record Result(long submitted,boolean cancelled) {}
    public Result edit(Context context,TerrainHeightmap map,ConfigManager config,AtomicBoolean cancel,IntConsumer progress) throws Exception {
        var s=map.settings;
        BlockState surface=block(s.surface()),sub=block(s.subsurface()),foundation=block(s.foundation());
        // Explicit world prevents a WorldEdit session world override or later teleport from redirecting this edit.
        // Actor supplies FAWE permissions, region restrictions and limits; disk-backed history bounds heap use.
        EditSession edit=WorldEdit.getInstance().newEditSessionBuilder().world(context.world).actor(context.actor)
                .fastMode(false).changeSet(true,context.actor.getUniqueId()).build();
        long submitted=0,visited=0;
        Throwable failure=null;
        try {
            // Never use fast mode: player history and FAWE region/limit checks remain active.
            if(edit.getChangeSet()==null) throw new IllegalStateException("FAWE did not create undo history; refusing to edit.");
            outer: for(int z=map.minZ;z<map.minZ+map.depth;z++) {
                for(int x=map.minX;x<map.minX+map.width;x++) {
                    if(cancel.get()) break outer;
                    if(!map.contains(x,z)) continue;
                    boolean edge=map.isBoundaryCell(x,z);
                    int top=map.height(x,z);
                    int ceiling=config.clearAbove ? Math.max(top,Math.min(s.maxY()-1,config.clearToY)) : top;
                    for(int y=s.bottom();y<=ceiling;y++) {
                        if(cancel.get()) break outer;
                        BlockVector3 position=BlockVector3.at(x,y,z);
                        String current=edit.getBlock(position).getBlockType().id();
                        // Selected road edge cells are fill-air-only, independent of the material mask.
                        if(edge && !current.equals("minecraft:air") && !current.equals("minecraft:cave_air") && !current.equals("minecraft:void_air")) continue;
                        if(config.protectedBlocks.contains(current.substring(current.indexOf(':')+1))) continue;
                        BlockState target=y>top ? BlockTypes.AIR.getDefaultState() : y==top ? surface : y>=top-config.depth ? sub : foundation;
                        if(++visited>config.maxBlocks) throw new IllegalStateException("Block submission limit exceeded.");
                        if(edit.setBlock(position,target)) submitted++;
                    }
                }
                progress.accept((z-map.minZ+1)*100/map.depth);
            }
        } catch(Throwable e) { failure=e; }
        finally {
            // Closing flushes queued FAWE changes and can block: always runs on the worker.
            try {edit.close();} catch(Throwable e) {if(failure==null) failure=e;else failure.addSuppressed(e);}
            try {context.history.remember(edit);} catch(Throwable e) {if(failure==null) failure=e;else failure.addSuppressed(e);}
        }
        if(failure!=null) throw new Exception("Edit failed after "+submitted+" accepted changes. Partial terrain may exist; use //undo. Check server log if history recovery fails.",failure);
        return new Result(submitted,cancel.get());
    }
    private static BlockState block(String material) {
        BlockType type=BlockTypes.get("minecraft:"+material.toLowerCase(Locale.ROOT));
        if(type==null) throw new IllegalArgumentException("FAWE does not recognise material "+material);
        return type.getDefaultState();
    }
}
