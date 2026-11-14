package org.sylvarion.terrainmesh.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.sylvarion.terrainmesh.selection.PointSelectionManager.Selection;
import java.util.*;

public final class ConfigManager {
    public final long maxArea,maxBlocks,maxMemory;
    public final int maxPoints,maxHills,depth,confirmationSeconds,particleBudget,previewSeconds,clearToY;
    public final boolean confirmation,preview,particles,boundary,mesh,controls,clearAbove;
    public final Set<String> protectedBlocks;
    public final String prefix;
    private final FileConfiguration config;
    public ConfigManager(FileConfiguration c) {
        config=c; maxArea=range(c,"generation.max-area",1000000,1,4000000);
        maxBlocks=range(c,"generation.max-blocks",50000000,1,Integer.MAX_VALUE);
        maxMemory=range(c,"generation.max-memory-mb",256,16,4096)*1048576;
        maxPoints=(int)range(c,"generation.max-points",512,3,2048);maxHills=(int)range(c,"generation.max-hills",128,0,512);
        depth=(int)range(c,"terrain.subsurface-depth",4,0,128);
        confirmationSeconds=(int)range(c,"generation.confirmation-seconds",120,10,3600);
        particleBudget=(int)range(c,"preview.particle-budget",500,10,2000);previewSeconds=(int)range(c,"preview.duration-seconds",60,1,600);
        confirmation=c.getBoolean("generation.require-confirmation",true);preview=c.getBoolean("preview.enabled",true);
        particles=c.getBoolean("preview.particles",true);boundary=c.getBoolean("preview.show-boundary",true);mesh=c.getBoolean("preview.show-mesh",true);controls=c.getBoolean("preview.show-control-points",true);
        clearAbove=c.getBoolean("generation.clear-above",false);clearToY=c.getInt("generation.clear-to-y",120);
        prefix=c.getString("messages.prefix","<gray>[<green>TerrainMesh<gray>] ");
        Set<String> protect=new HashSet<>();
        if(c.getBoolean("protection.enabled",true)) for(String name:c.getStringList("protection.never-overwrite")) protect.add(material(name).toLowerCase(Locale.ROOT));
        protectedBlocks=Set.copyOf(protect);
        defaults();
    }
    public Selection defaults() {
        return new Selection((int)range(config,"generation.smoothing",70,0,100),(int)range(config,"generation.resolution",1,1,8),
                (int)range(config,"generation.noise",0,0,100),config.getInt("terrain.default-bottom-y",0),
                material(config.getString("terrain.surface","GRASS_BLOCK")),material(config.getString("terrain.subsurface","DIRT")),material(config.getString("terrain.foundation","STONE")));
    }
    public static String material(String name) {
        Material m=Material.matchMaterial(name);
        if(m==null || !m.isBlock() || m.isAir() || !m.isSolid()) throw new IllegalArgumentException("Expected a solid block material: "+name);
        return m.name();
    }
    private static long range(FileConfiguration c,String path,long fallback,long min,long max) {
        long value=c.getLong(path,fallback); if(value<min || value>max) throw new IllegalArgumentException(path+" must be "+min+".."+max); return value;
    }
}
