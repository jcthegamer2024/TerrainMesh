package org.sylvarion.terrainmesh.mesh;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.sylvarion.terrainmesh.selection.PointSelectionManager.Snapshot;
import java.util.*;
import java.util.function.*;

/** Dense bounded raster. Outside cells are NaN; integer lattice positions denote block centers. */
public final class TerrainHeightmap {
    public final int minX,minZ,width,depth;
    public final Polygon polygon;
    public final MeshGenerator mesh;
    private final float[] heights;
    private final BitSet edgeCells;
    public final long estimatedBlocks, columns, memoryBytes;
    public final int minHeight,maxHeight;
    public final Snapshot settings;
    private TerrainHeightmap(int x,int z,int w,int d,Polygon p,MeshGenerator m,float[] h,BitSet edgeCells,Snapshot s,long blocks,long columns,long bytes,int low,int high) {
        minX=x; minZ=z; width=w; depth=d; polygon=p; mesh=m; heights=h; settings=s;
        estimatedBlocks=blocks; this.columns=columns; memoryBytes=bytes; minHeight=low; maxHeight=high;
        this.edgeCells=edgeCells;
    }
    public static long estimateMemory(long cells,int points) { return Math.addExact(Math.multiplyExact(cells,18L),points*4096L+1048576); }
    public static int clampHeight(double h,int min,int max) { return (int)Math.clamp(Math.round(h),min,max-1); }
    public int height(int x,int z) {return clampHeight(heights[(z-minZ)*width+x-minX],settings.minY(),settings.maxY());}
    public boolean isBoundaryCell(int x,int z) {return edgeCells.get((z-minZ)*width+x-minX);}
    public boolean contains(int x,int z) {return x>=minX && z>=minZ && x<minX+width && z<minZ+depth && Float.isFinite(heights[(z-minZ)*width+x-minX]);}
    public static TerrainHeightmap build(Snapshot s,long maxArea,long maxMemory,long maxBlocks,BooleanSupplier cancelled,IntConsumer progress) {
        Polygon p=PolygonValidator.validate(s.boundary(),s.hills(),maxArea);
        if(s.bottom()<s.minY() || s.bottom()>=s.maxY()) throw new IllegalArgumentException("Bottom Y is outside world limits.");
        for(var point:s.boundary()) validateHeight(point.y(),s);
        for(var hill:s.hills()) validateHeight(hill.position().y(),s);
        Envelope e=p.getEnvelopeInternal(); int x=(int)e.getMinX(),z=(int)e.getMinY();
        long wl=(long)e.getMaxX()-x+1, dl=(long)e.getMaxY()-z+1;
        long cells=Math.multiplyExact(wl,dl), bytes=estimateMemory(cells,s.boundary().size()+s.hills().size());
        long available=Runtime.getRuntime().maxMemory()-Runtime.getRuntime().totalMemory()+Runtime.getRuntime().freeMemory();
        if(cells>Integer.MAX_VALUE-8 || cells>maxArea*4L || bytes>maxMemory || bytes>available/2)
            throw new IllegalArgumentException("Bounding raster exceeds memory/area budget (estimated "+bytes/1048576+" MiB). Split the selection.");
        int w=(int)wl,d=(int)dl; float[] h=new float[(int)cells]; Arrays.fill(h,Float.NaN);
        BitSet fixed=new BitSet(h.length);
        MeshGenerator mesh=new MeshGenerator(p,s.boundary());
        TerrainInterpolator interpolation=new TerrainInterpolator(mesh,s.boundary(),s.hills());
        var locator=new IndexedPointInAreaLocator(p);
        for(int row=0;row<d;row++) {
            check(cancelled);
            for(int col=0;col<w;col++) {
                int xx=x+col,zz=z+row,i=row*w+col;
                if(locator.locate(new Coordinate(xx,zz))==Location.EXTERIOR) continue;
                h[i]=(float)interpolation.height(xx,zz);
                // Lock the inner boundary band to its nearest edge, including diagonal/concave edges.
                var edge=interpolation.edge(xx,zz);
                if(edge.distance()<=0.71) { h[i]=(float)edge.height(); fixed.set(i); }
            }
            progress.accept((row+1)*50/d);
        }
        BitSet edgeCells=(BitSet)fixed.clone();
        for(var hill:s.hills()) { var a=hill.position(); int i=(a.z()-z)*w+a.x()-x; h[i]=a.y(); fixed.set(i); }
        // Constrained diffusion removes triangle creases; no fixed boundary/control cell is moved.
        int iterations=s.smoothing()*2, stride=s.resolution(); float[] next=h.clone();
        for(int pass=0;pass<iterations;pass++) {
            check(cancelled);
            for(int row=1;row<d-1;row++) for(int col=1;col<w-1;col++) {
                int i=row*w+col;
                if(!Float.isFinite(h[i]) || fixed.get(i)) continue;
                double sum=0; int n=0;
                for(int[] delta:DIRECTIONS) {
                    // Every traversed cell must be inside, preventing smoothing across a narrow exterior notch.
                    for(int k=1;k<=stride;k++) {
                        int r=row+delta[1]*k,c=col+delta[0]*k;
                        if(r<0 || c<0 || r>=d || c>=w || !Float.isFinite(h[r*w+c])) break;
                        sum+=h[r*w+c]; n++;
                    }
                }
                if(n>0) next[i]=(float)(h[i]*0.5+sum/n*0.5);
            }
            float[] swap=h;h=next;next=swap;
            if(pass%10==0) progress.accept(50+pass*40/Math.max(1,iterations));
        }
        long blocks=0,columns=0; int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
        for(int row=0;row<d;row++) {
            check(cancelled);
            for(int col=0;col<w;col++) {
                int i=row*w+col; if(!Float.isFinite(h[i])) continue;
                if(!fixed.get(i) && s.noise()>0) h[i]+=(float)(s.noise()/10.0*interpolation.attenuation(x+col,z+row)*TerrainInterpolator.noise((x+col)/32.0,(z+row)/32.0,s.seed()));
                int top=clampHeight(h[i],s.minY(),s.maxY());
                if(top<s.bottom()) throw new IllegalArgumentException("Bottom Y exceeds part of the terrain surface.");
                low=Math.min(low,top);high=Math.max(high,top);columns++;blocks+=top-(long)s.bottom()+1;
            }
        }
        if(blocks>maxBlocks) throw new IllegalArgumentException("Estimated fill "+blocks+" exceeds max-blocks "+maxBlocks+".");
        progress.accept(100);
        return new TerrainHeightmap(x,z,w,d,p,mesh,h,edgeCells,s,blocks,columns,bytes,low,high);
    }
    private static final int[][] DIRECTIONS={{1,0},{-1,0},{0,1},{0,-1}};
    private static void validateHeight(int y,Snapshot s) {
        if(y<s.minY() || y>=s.maxY()) throw new IllegalArgumentException("Control point Y is outside world height limits.");
        if(y<s.bottom()) throw new IllegalArgumentException("A control point is below bottom Y.");
    }
    private static void check(BooleanSupplier cancelled) {if(cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Planning cancelled.");}
}
