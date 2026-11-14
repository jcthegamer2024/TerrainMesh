package org.sylvarion.terrainmesh;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.sylvarion.terrainmesh.commands.TerrainMeshCommand;
import org.sylvarion.terrainmesh.config.ConfigManager;
import org.sylvarion.terrainmesh.selection.*;
import org.sylvarion.terrainmesh.generation.*;
import org.sylvarion.terrainmesh.preview.TerrainPreviewManager;
import java.util.*;

public final class TerrainMeshPlugin extends JavaPlugin implements Listener {
    private ConfigManager settings;
    private final PointSelectionManager selections=new PointSelectionManager();
    private final TerrainJobManager jobs=new TerrainJobManager();
    private TerrainPreviewManager previews;
    private TerrainGenerator generator;
    private NamespacedKey wandKey;
    @Override public void onEnable() {
        saveDefaultConfig();
        try {settings=new ConfigManager(getConfig());} catch(IllegalArgumentException e) {getLogger().severe(e.getMessage());getServer().getPluginManager().disablePlugin(this);return;}
        wandKey=new NamespacedKey(this,"selection_wand");
        previews=new TerrainPreviewManager(this);generator=new TerrainGenerator(this);
        var command=new TerrainMeshCommand(this);Objects.requireNonNull(getCommand("terrainmesh")).setExecutor(command);getCommand("terrainmesh").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskTimer(this,()->{
            generator.expire();
            for(Player p:Bukkit.getOnlinePlayers()) {var j=jobs.get(p.getUniqueId());if(j!=null)tell(p,"<gray>Terrain job: "+j.phase+" "+j.progress.get()+"%"+(j.cancel.get()?" (stopping)":""));}
        },100,100);
    }
    @Override public void onDisable() {jobs.shutdown();if(previews!=null)previews.close();getLogger().info("Stopping terrain jobs. In-flight FAWE edits flush and retain history; partial edits may require //undo.");}
    public ConfigManager settings() {return settings;}
    public TerrainJobManager jobs() {return jobs;}
    public TerrainGenerator generator() {return generator;}
    public TerrainPreviewManager previews() {return previews;}
    public PointSelectionManager.Selection selection(Player player) {return selections.get(player.getUniqueId(),settings::defaults);}
    public void tell(Player p,String text) {p.sendMessage(MiniMessage.miniMessage().deserialize(settings.prefix+text));}
    public void tell(UUID id,String text) {sync(()->{var p=Bukkit.getPlayer(id);if(p!=null)tell(p,text);else getLogger().info(id+": "+text);});}
    public void sync(Runnable action) {
        if(!isEnabled()) return;
        if(Bukkit.isPrimaryThread())action.run();else {try {Bukkit.getScheduler().runTask(this,action);}catch(org.bukkit.plugin.IllegalPluginAccessException ignored){getLogger().info("Completion arrived after plugin disable.");}}
    }
    public void changed(Player p) {selection(p).revision++;generator.invalidate(p.getUniqueId());}
    public void mutable(Player p) {if(jobs.get(p.getUniqueId())!=null)throw new IllegalArgumentException("Cancel or finish your current job before changing the selection.");}
    public BoundaryPoint target(Player p) {
        var block=p.getTargetBlockExact(128);
        if(block==null)throw new IllegalArgumentException("Target a block within 128 blocks.");
        return new BoundaryPoint(block.getX(),block.getY(),block.getZ());
    }
    public void add(Player p,BoundaryPoint point,boolean hill) {
        mutable(p);var s=selection(p);
        if(s.world!=null && !s.world.equals(p.getWorld().getUID()))throw new IllegalArgumentException("Clear your selection before selecting in another world.");
        if(hill) {
            if(s.hills.size()>=settings.maxHills)throw new IllegalArgumentException("Hill limit reached.");
            for(var h:s.hills)if(h.position().x()==point.x() && h.position().z()==point.z())throw new IllegalArgumentException("A hill already occupies that X/Z position.");
            s.hills.add(new InteriorPoint(s.nextId++,point));
        } else {
            if(s.boundary.size()>=settings.maxPoints)throw new IllegalArgumentException("Boundary point limit reached.");
            for(var b:s.boundary)if(b.x()==point.x() && b.z()==point.z())throw new IllegalArgumentException("A boundary point already occupies that X/Z position.");
            s.boundary.add(point);
        }
        s.world=p.getWorld().getUID();changed(p);tell(p,"<green>Added "+(hill?"hill "+(s.nextId-1):"boundary point "+s.boundary.size())+" at "+point.x()+", "+point.y()+", "+point.z()+".");
    }
    public void undo(Player p) {mutable(p);var s=selection(p);if(s.boundary.isEmpty())throw new IllegalArgumentException("No boundary points to remove.");s.boundary.removeLast();changed(p);tell(p,"<yellow>Removed last boundary point.");}
    public void wand(Player p) {
        ItemStack item=new ItemStack(Material.BLAZE_ROD);var meta=item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize("<green>TerrainMesh point wand"));
        meta.getPersistentDataContainer().set(wandKey,PersistentDataType.BYTE,(byte)1);item.setItemMeta(meta);
        if(!p.getInventory().addItem(item).isEmpty())throw new IllegalArgumentException("Make room in your inventory first.");
        tell(p,"<green>Left-click: add boundary point. Right-click: remove last point.");
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND || e.getItem()==null || !e.getItem().hasItemMeta() || !e.getItem().getItemMeta().getPersistentDataContainer().has(wandKey,PersistentDataType.BYTE))return;
        e.setCancelled(true);if(!e.getPlayer().hasPermission("terrainmesh.use"))return;
        try {
            if(e.getAction()==Action.LEFT_CLICK_BLOCK && e.getClickedBlock()!=null) {var b=e.getClickedBlock();add(e.getPlayer(),new BoundaryPoint(b.getX(),b.getY(),b.getZ()),false);}
            else if(e.getAction()==Action.RIGHT_CLICK_AIR || e.getAction()==Action.RIGHT_CLICK_BLOCK)undo(e.getPlayer());
        }catch(IllegalArgumentException ex){tell(e.getPlayer(),"<red>"+ex.getMessage());}
    }
    @EventHandler public void quit(PlayerQuitEvent e) {UUID id=e.getPlayer().getUniqueId();jobs.cancel(id);generator.invalidate(id);selections.remove(id);}
    @EventHandler public void worldChange(PlayerChangedWorldEvent e) {jobs.cancel(e.getPlayer().getUniqueId());generator.invalidate(e.getPlayer().getUniqueId());}
}
