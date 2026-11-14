package org.sylvarion.terrainmesh.mesh;

import org.sylvarion.terrainmesh.selection.*;
import java.util.*;

/** Boundary constrained Shepard corrections; exact hill constraints, coherent seeded noise. */
public final class TerrainInterpolator {
    public record EdgeSample(double distance,double height) {}
    private final List<BoundaryPoint> boundary;
    private final List<InteriorPoint> hills;
    private final MeshGenerator mesh;
    private final double[] corrections;
    public TerrainInterpolator(MeshGenerator mesh,List<BoundaryPoint> boundary,List<InteriorPoint> hills) {
        this.mesh=mesh; this.boundary=boundary; this.hills=hills;
        corrections=new double[hills.size()];
        for(int i=0;i<hills.size();i++) { var p=hills.get(i).position(); corrections[i]=p.y()-mesh.height(p.x(),p.z()); }
    }
    public EdgeSample edge(double x,double z) {
        double best=Double.POSITIVE_INFINITY, height=0;
        for(int i=0;i<boundary.size();i++) {
            var a=boundary.get(i); var b=boundary.get((i+1)%boundary.size());
            double dx=(double)b.x()-a.x(), dz=(double)b.z()-a.z();
            double t=Math.clamp(((x-a.x())*dx+(z-a.z())*dz)/(dx*dx+dz*dz),0,1);
            double distance=Math.hypot(x-a.x()-t*dx,z-a.z()-t*dz);
            if(distance<best) {best=distance; height=a.y()+t*(b.y()-a.y());}
        }
        return new EdgeSample(best,height);
    }
    public double height(double x,double z) {
        EdgeSample e=edge(x,z);
        if(e.distance<1e-8) return e.height;
        double sum=1/(e.distance*e.distance), offset=0;
        for(int i=0;i<hills.size();i++) {
            var p=hills.get(i).position(); double d2=(x-p.x())*(x-p.x())+(z-p.z())*(z-p.z());
            if(d2<1e-12) return p.y();
            double w=1/d2; sum+=w; offset+=w*corrections[i];
        }
        return mesh.height(x,z)+offset/sum;
    }
    public double attenuation(double x,double z) {
        double d=edge(x,z).distance;
        for(var h:hills) d=Math.min(d,Math.hypot(x-h.position().x(),z-h.position().z()));
        double t=Math.clamp(d/12,0,1); return t*t*(3-2*t);
    }
    public static double noise(double x,double z,long seed) {
        int ix=(int)Math.floor(x), iz=(int)Math.floor(z); double u=x-ix,v=z-iz;
        u=u*u*(3-2*u); v=v*v*(3-2*v);
        return mix(mix(hash(ix,iz,seed),hash(ix+1,iz,seed),u),mix(hash(ix,iz+1,seed),hash(ix+1,iz+1,seed),u),v);
    }
    private static double mix(double a,double b,double t) {return a+(b-a)*t;}
    private static double hash(int x,int z,long seed) {
        long h=seed+x*0x9E3779B97F4A7C15L+z*0xC2B2AE3D27D4EB4FL;
        h=(h^(h>>>30))*0xBF58476D1CE4E5B9L; h=(h^(h>>>27))*0x94D049BB133111EBL;
        return ((h^(h>>>31))>>>11)*0x1.0p-53*2-1;
    }
}
