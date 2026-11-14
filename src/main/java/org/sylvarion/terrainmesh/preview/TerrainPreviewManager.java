package org.sylvarion.terrainmesh.preview;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.sylvarion.terrainmesh.TerrainMeshPlugin;
import org.sylvarion.terrainmesh.mesh.*;
import org.sylvarion.terrainmesh.selection.PointSelectionManager.Snapshot;
import java.util.*;

/** Player-only bounded particles; no entities, chunks or world blocks are created. */
public final class TerrainPreviewManager {
    private record Dot(double x,double y,double z,Color color) {}
    private record Preview(List<Dot> dots,long expires) {}
    private final TerrainMeshPlugin plugin;
    private final Map<UUID,Preview> previews=new HashMap<>();
    private final BukkitTask task;
    public TerrainPreviewManager(TerrainMeshPlugin plugin) {
        this.plugin=plugin;
        task=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            long now=System.currentTimeMillis();previews.entrySet().removeIf(e->e.getValue().expires<now);
            for(Player player:Bukkit.getOnlinePlayers()) {
                if(!player.hasPermission("terrainmesh.use")) continue;
                var selection=plugin.selection(player);if(selection.world==null || !selection.world.equals(player.getWorld().getUID())) continue;
                var preview=previews.get(player.getUniqueId());
                if(preview!=null) draw(player,preview.dots);
                else if(plugin.settings().particles) draw(player,selectionDots(selection.snapshot(player.getWorld().getMinHeight(),player.getWorld().getMaxHeight())));
            }
        },10,20);
    }
    public void show(Player player,TerrainHeightmap map) {
        if(!plugin.settings().particles) return;
        List<Dot> dots=selectionDots(map.settings);int budget=plugin.settings().particleBudget;
        if(plugin.settings().mesh) for(var t:map.mesh.triangles()) {
            line(dots,t.a().x(),t.a().y()+1.15,t.a().z(),t.b().x(),t.b().y()+1.15,t.b().z(),Color.AQUA,Math.max(1,budget/3));
            line(dots,t.b().x(),t.b().y()+1.15,t.b().z(),t.c().x(),t.c().y()+1.15,t.c().z(),Color.AQUA,Math.max(1,budget/3));
            line(dots,t.c().x(),t.c().y()+1.15,t.c().z(),t.a().x(),t.a().y()+1.15,t.a().z(),Color.AQUA,Math.max(1,budget/3));
            if(dots.size()>=budget*2) break;
        }
        // Stratified raster sample, bounded independently from selection/mesh complexity.
        int stride=Math.max(map.settings.resolution(),(int)Math.ceil(Math.sqrt((double)map.width*map.depth/Math.max(1,budget/2))));
        List<Dot> surface=new ArrayList<>();
        for(int z=map.minZ;z<map.minZ+map.depth;z+=stride) for(int x=map.minX;x<map.minX+map.width;x+=stride)
            if(map.contains(x,z)) surface.add(new Dot(x+.5,map.height(x,z)+1.1,z+.5,Color.LIME));
        List<Dot> result=new ArrayList<>(limit(dots,budget/2));result.addAll(limit(surface,budget/2));
        previews.put(player.getUniqueId(),new Preview(result,System.currentTimeMillis()+plugin.settings().previewSeconds*1000L));
    }
    public void clear(UUID id) {previews.remove(id);}
    private List<Dot> selectionDots(Snapshot s) {
        List<Dot> dots=new ArrayList<>(); int budget=plugin.settings().particleBudget;
        if(plugin.settings().boundary) for(int i=0;i<s.boundary().size();i++) {
            var a=s.boundary().get(i);var b=s.boundary().get((i+1)%s.boundary().size());
            line(dots,a.x(),a.y()+1.1,a.z(),b.x(),b.y()+1.1,b.z(),Color.YELLOW,Math.max(1,budget/Math.max(1,s.boundary().size())));
        }
        if(plugin.settings().controls) {
            for(var a:s.boundary()) dots.add(new Dot(a.x()+.5,a.y()+1.5,a.z()+.5,Color.ORANGE));
            for(var h:s.hills()) {var a=h.position();dots.add(new Dot(a.x()+.5,a.y()+1.5,a.z()+.5,Color.FUCHSIA));}
        }
        return limit(dots,budget);
    }
    private static void line(List<Dot> dots,double ax,double ay,double az,double bx,double by,double bz,Color color,int budget) {
        int steps=Math.max(1,Math.min(budget,(int)Math.ceil(Math.hypot(bx-ax,bz-az)/2)));
        for(int i=0;i<steps;i++) {double t=(double)i/steps;dots.add(new Dot(ax+(bx-ax)*t+.5,ay+(by-ay)*t,az+(bz-az)*t+.5,color));}
    }
    private static List<Dot> limit(List<Dot> dots,int budget) {
        if(dots.size()<=budget)return dots;List<Dot> out=new ArrayList<>();
        for(int i=0;i<budget;i++)out.add(dots.get((int)((long)i*dots.size()/budget)));return out;
    }
    private void draw(Player player,List<Dot> dots) {
        for(var d:dots) if(player.getLocation().distanceSquared(new Location(player.getWorld(),d.x,d.y,d.z))<192*192)
            player.spawnParticle(Particle.DUST,d.x,d.y,d.z,1,0,0,0,0,new Particle.DustOptions(d.color,1.2f));
    }
    public void close() {task.cancel();previews.clear();}
}
