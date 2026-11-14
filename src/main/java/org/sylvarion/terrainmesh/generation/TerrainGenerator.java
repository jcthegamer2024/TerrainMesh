package org.sylvarion.terrainmesh.generation;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.sylvarion.terrainmesh.TerrainMeshPlugin;
import org.sylvarion.terrainmesh.integration.FAWEBridge;
import org.sylvarion.terrainmesh.mesh.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

public final class TerrainGenerator {
    private record Confirmation(TerrainHeightmap map,long expires) {}
    private final TerrainMeshPlugin plugin;
    private final FAWEBridge bridge=new FAWEBridge();
    private final Map<UUID,Confirmation> confirmations=new HashMap<>();
    public TerrainGenerator(TerrainMeshPlugin plugin) {this.plugin=plugin;}
    public void invalidate(UUID id) {confirmations.remove(id);plugin.previews().clear(id);}
    public void expire() {confirmations.entrySet().removeIf(e->e.getValue().expires<System.currentTimeMillis());}
    public void prepare(Player player,boolean preview) {
        var config=plugin.settings();
        if(preview && !config.preview) throw new IllegalArgumentException("Previews are disabled.");
        var selection=plugin.selection(player);
        if(selection.world==null || !selection.world.equals(player.getWorld().getUID())) throw new IllegalArgumentException("Select points in your current world first.");
        var s=selection.snapshot(player.getWorld().getMinHeight(),player.getWorld().getMaxHeight());
        UUID id=player.getUniqueId();
        var polygon=PolygonValidator.validate(s.boundary(),s.hills(),config.maxArea);
        var job=plugin.jobs().reserve(id,s.world(),polygon.getEnvelopeInternal(),false);
        // Bound retained raster memory to one plan, independent of player count.
        for(UUID old:confirmations.keySet()) if(!old.equals(id)) plugin.tell(old,"<yellow>Your terrain confirmation expired because another plan was requested.");
        confirmations.clear();plugin.previews().clear(id);
        plugin.jobs().execute(id,()->{
            try {
                job.phase="planning";
                var map=TerrainHeightmap.build(s,config.maxArea,config.maxMemory,config.maxBlocks,job.cancel::get,job.progress::set);
                long estimate=map.estimatedBlocks;
                if(config.clearAbove) estimate+=map.columns*Math.max(0,Math.min(s.maxY()-1,config.clearToY)-map.minHeight);
                if(estimate>config.maxBlocks) throw new IllegalArgumentException("Fill plus clearing estimate exceeds max-blocks.");
                long finalEstimate=estimate;
                plugin.jobs().complete(id,job);
                plugin.sync(()->{
                    Player p=Bukkit.getPlayer(id);
                    if(p==null || job.cancel.get() || plugin.selection(p).revision!=s.revision() || !p.getWorld().getUID().equals(s.world()))return;
                    confirmations.clear();
                    confirmations.put(id,new Confirmation(map,System.currentTimeMillis()+config.confirmationSeconds*1000L));
                    if(preview) plugin.previews().show(p,map);
                    plugin.tell(p,"<green>Plan ready: area "+String.format(Locale.ROOT,"%.1f",map.polygon.getArea())+"; columns "+map.columns+"; boundary points "+s.boundary().size()+"; hills "+s.hills().size()+"; heights "+map.minHeight+".."+map.maxHeight+"; at most "+finalEstimate+" block submissions; raster budget "+map.memoryBytes/1048576+" MiB.");
                    if(config.clearAbove) plugin.tell(p,"<red>Clearing above the surface is enabled through Y="+config.clearToY+".");
                    if(!config.confirmation && !preview) confirm(p);
                    else plugin.tell(p,"<yellow>Review the selection, then /terrainmesh generate confirm within "+config.confirmationSeconds+" seconds.");
                });
            } catch(CancellationException e) {plugin.tell(id,"<yellow>Terrain planning cancelled.");}
            catch(Exception e) {plugin.tell(id,"<red>Planning failed: "+safe(e));plugin.getLogger().log(Level.WARNING,"Terrain planning failed for "+id,e);}
        });
        plugin.tell(player,"<gray>Terrain plan queued. Geometry is calculated asynchronously.");
    }
    public void confirm(Player player) {
        if(!player.hasPermission("terrainmesh.generate")) throw new IllegalArgumentException("Missing terrainmesh.generate permission.");
        UUID id=player.getUniqueId();var ready=confirmations.get(id);
        if(ready==null || ready.expires<System.currentTimeMillis() || ready.map.settings.revision()!=plugin.selection(player).revision || !ready.map.settings.world().equals(player.getWorld().getUID())) {
            invalidate(id);throw new IllegalArgumentException("No current confirmation. Run /terrainmesh generate or preview first.");
        }
        var map=ready.map;var job=plugin.jobs().reserve(id,map.settings.world(),map.polygon.getEnvelopeInternal(),true);
        FAWEBridge.Context context;
        try { context=bridge.context(player); } catch(RuntimeException e) {plugin.jobs().cancel(id);plugin.jobs().execute(id,()->{});throw e;}
        confirmations.remove(id);plugin.previews().clear(id);
        plugin.jobs().execute(id,()->{
            if(job.cancel.get()) {plugin.tell(id,"<yellow>Queued generation cancelled; no blocks submitted.");return;}
            job.phase="editing";
            try {
                var result=bridge.edit(context,map,plugin.settings(),job.cancel,job.progress::set);
                plugin.tell(id,(result.cancelled()?"<yellow>Generation stopped; partial terrain may remain. ":"<green>Generation completed. ")+result.submitted()+" accepted block changes. Use //undo to restore through FAWE history.");
            } catch(Exception e) {plugin.tell(id,"<red>"+safe(e));plugin.getLogger().log(Level.SEVERE,"Terrain edit failed for "+id,e);}
        });
        plugin.tell(player,"<yellow>Generation queued. Cancellation stops further submissions; queued FAWE changes still flush.");
    }
    private static String safe(Exception e) {return String.valueOf(e.getMessage()).replace('<','[').replace('>',']');}
}
