package org.sylvarion.terrainmesh.commands;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.sylvarion.terrainmesh.TerrainMeshPlugin;
import org.sylvarion.terrainmesh.config.ConfigManager;
import org.sylvarion.terrainmesh.selection.*;
import java.util.*;

public final class TerrainMeshCommand implements CommandExecutor,TabCompleter {
    private final TerrainMeshPlugin plugin;
    private static final List<String> COMMANDS=List.of("wand","add","undo","clear","list","preview","generate","cancel","help","hill","smooth","resolution","bottom","material","noise","seed","sample");
    public TerrainMeshCommand(TerrainMeshPlugin plugin) {this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)) {sender.sendMessage("TerrainMesh commands require a player.");return true;}
        if(!p.hasPermission("terrainmesh.use")) {plugin.tell(p,"<red>Missing terrainmesh.use permission.");return true;}
        try {
            String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);var s=plugin.selection(p);
            switch(sub) {
                case "help" -> {
                    plugin.tell(p,"<green>/terrainmesh wand | add | undo | clear | list | preview | generate [confirm] | cancel");
                    plugin.tell(p,"<green>/terrainmesh hill add | remove ID | list | clear");
                    plugin.tell(p,"<green>/terrainmesh smooth 0-100 | resolution 1-8 | noise 0-100 | seed NUMBER | bottom Y");
                    plugin.tell(p,"<green>/terrainmesh material surface|subsurface|foundation BLOCK | sample SPACING");
                    plugin.tell(p,"<gray>Points are ordered around the perimeter. Y is the top occupied block. Select inner road edges and review the preview before confirming.");
                }
                case "wand" -> {arity(args,1);plugin.wand(p);}
                case "add" -> {arity(args,1);plugin.add(p,plugin.target(p),false);}
                case "undo" -> {arity(args,1);plugin.undo(p);}
                case "clear" -> {arity(args,1);plugin.mutable(p);s.boundary.clear();s.hills.clear();s.nextId=1;s.world=null;plugin.changed(p);plugin.tell(p,"<yellow>Cleared boundary and hill points.");}
                case "list" -> {arity(args,1);plugin.tell(p,"<gray>Boundary in world "+s.world+":");for(int i=0;i<s.boundary.size();i++)point(p,"Point "+(i+1),s.boundary.get(i));}
                case "hill" -> hill(p,args);
                case "preview" -> {arity(args,1);plugin.generator().prepare(p,true);}
                case "generate" -> {
                    if(!p.hasPermission("terrainmesh.generate"))throw new IllegalArgumentException("Missing terrainmesh.generate permission.");
                    if(args.length==1)plugin.generator().prepare(p,false);
                    else if(args.length==2 && args[1].equalsIgnoreCase("confirm"))plugin.generator().confirm(p);
                    else throw new IllegalArgumentException("Usage: /terrainmesh generate [confirm]");
                }
                case "cancel" -> {arity(args,1);plugin.generator().invalidate(p.getUniqueId());boolean cancelled=plugin.jobs().cancel(p.getUniqueId());plugin.tell(p,"<yellow>Preview and confirmation cleared."+(cancelled?" Job cancellation requested; queued FAWE changes may still complete. Use //undo for partial terrain.":""));}
                case "smooth","resolution","noise","bottom","seed" -> {
                    arity(args,2);plugin.mutable(p);
                    switch(sub) {
                        case "smooth" -> s.smoothing=number(args[1],0,100);
                        case "resolution" -> s.resolution=number(args[1],1,8);
                        case "noise" -> s.noise=number(args[1],0,100);
                        case "bottom" -> s.bottom=number(args[1],p.getWorld().getMinHeight(),p.getWorld().getMaxHeight()-1);
                        case "seed" -> s.seed=Long.parseLong(args[1]);
                    }
                    plugin.changed(p);plugin.tell(p,"<green>Updated "+sub+" to "+args[1]+". Previous confirmation invalidated.");
                }
                case "material" -> {
                    arity(args,3);plugin.mutable(p);String material=ConfigManager.material(args[2]);
                    switch(args[1].toLowerCase(Locale.ROOT)) {
                        case "surface" -> s.surface=material;case "subsurface" -> s.subsurface=material;case "foundation" -> s.foundation=material;
                        default -> throw new IllegalArgumentException("Layer must be surface, subsurface or foundation.");
                    }
                    plugin.changed(p);plugin.tell(p,"<green>Material updated to "+material+".");
                }
                case "sample" -> {arity(args,2);sample(p,number(args[1],1,64));}
                default -> throw new IllegalArgumentException("Unknown command. Use /terrainmesh help.");
            }
        }catch(NumberFormatException e){plugin.tell(p,"<red>Expected a valid integer.");}
        catch(IllegalArgumentException e){plugin.tell(p,"<red>"+e.getMessage().replace('<','[').replace('>',']'));}
        return true;
    }
    private void hill(Player p,String[] args) {
        if(args.length<2)throw new IllegalArgumentException("Usage: hill add | remove ID | list | clear");
        var s=plugin.selection(p);
        switch(args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {arity(args,2);plugin.add(p,plugin.target(p),true);}
            case "list" -> {arity(args,2);for(var h:s.hills)point(p,"Hill "+h.id(),h.position());}
            case "remove" -> {arity(args,3);plugin.mutable(p);int id=number(args[2],1,Integer.MAX_VALUE);if(!s.hills.removeIf(h->h.id()==id))throw new IllegalArgumentException("No hill with that ID.");plugin.changed(p);plugin.tell(p,"<yellow>Hill removed.");}
            case "clear" -> {arity(args,2);plugin.mutable(p);s.hills.clear();plugin.changed(p);plugin.tell(p,"<yellow>Hill points cleared.");}
            default -> throw new IllegalArgumentException("Usage: hill add | remove ID | list | clear");
        }
    }
    private void sample(Player p,int spacing) {
        plugin.mutable(p);var s=plugin.selection(p);
        if(s.boundary.size()<3 || !p.getWorld().getUID().equals(s.world))throw new IllegalArgumentException("Select a polygon in this world first.");
        List<BoundaryPoint> sampled=new ArrayList<>();Set<String> seen=new HashSet<>();int added=0;
        for(int i=0;i<s.boundary.size();i++) {
            var a=s.boundary.get(i);var b=s.boundary.get((i+1)%s.boundary.size());
            if(seen.add(a.x()+":"+a.z()))sampled.add(a);
            int steps=(int)Math.ceil(Math.hypot((double)b.x()-a.x(),(double)b.z()-a.z())/spacing);
            if(steps>plugin.settings().maxPoints)throw new IllegalArgumentException("Sampling would exceed the point budget. Increase spacing.");
            for(int k=1;k<steps;k++) {
                double t=(double)k/steps;int x=(int)Math.round(a.x()+((double)b.x()-a.x())*t),z=(int)Math.round(a.z()+((double)b.z()-a.z())*t),y=(int)Math.round(a.y()+((double)b.y()-a.y())*t);
                if((x==a.x() && z==a.z()) || (x==b.x() && z==b.z()))continue;
                if(!p.getWorld().isChunkLoaded(x>>4,z>>4))throw new IllegalArgumentException("Sampling requires loaded edge chunks. No selection changes were applied.");
                if(!seen.add(x+":"+z))continue;
                Integer found=null;
                for(int offset=0;offset<=8 && found==null;offset++) for(int sign:new int[]{1,-1}) {
                    int yy=y+offset*sign;if(yy<p.getWorld().getMinHeight() || yy>=p.getWorld().getMaxHeight()-1)continue;
                    var block=p.getWorld().getBlockAt(x,yy,z);
                    if(block.getType().isSolid() && !block.getRelative(0,1,0).getType().isSolid()) {found=yy;break;}
                }
                if(found==null)throw new IllegalArgumentException("No exposed solid edge within 8 blocks of expected Y at "+x+", "+z+". Selection unchanged.");
                sampled.add(new BoundaryPoint(x,found,z));added++;
                if(sampled.size()>plugin.settings().maxPoints)throw new IllegalArgumentException("Sampling exceeds point budget. Selection unchanged.");
            }
        }
        org.sylvarion.terrainmesh.mesh.PolygonValidator.validate(sampled,s.hills,plugin.settings().maxArea);
        s.boundary.clear();s.boundary.addAll(sampled);plugin.changed(p);plugin.tell(p,"<green>Sampled "+added+" additional edge heights. Review the preview; sampling does not trace road curves.");
    }
    private void point(Player p,String label,BoundaryPoint a) {plugin.tell(p,"<gray>"+label+": "+a.x()+", "+a.y()+", "+a.z());}
    private static void arity(String[] args,int length) {if(args.length!=length)throw new IllegalArgumentException("Unexpected arguments. Use /terrainmesh help.");}
    private static int number(String s,int min,int max) {int value=Integer.parseInt(s);if(value<min || value>max)throw new IllegalArgumentException("Value must be "+min+".."+max+".");return value;}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> options=args.length==1?COMMANDS:args.length==2?switch(args[0].toLowerCase(Locale.ROOT)) {
            case "hill"->List.of("add","remove","list","clear");case "material"->List.of("surface","subsurface","foundation");case "generate"->List.of("confirm");default->List.of();
        }:List.of();
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);return options.stream().filter(o->o.startsWith(prefix)).toList();
    }
}
