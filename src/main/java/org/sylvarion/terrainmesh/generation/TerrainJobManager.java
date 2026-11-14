package org.sylvarion.terrainmesh.generation;

import org.locationtech.jts.geom.Envelope;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** One worker bounds aggregate raster allocation. Area leases include FAWE flush/history. */
public final class TerrainJobManager {
    public static final class Job {
        public final AtomicBoolean cancel=new AtomicBoolean();
        public final AtomicInteger progress=new AtomicInteger();
        public volatile String phase="queued";
    }
    private record Lease(UUID world,Envelope bounds) {}
    private final Map<UUID,Job> jobs=new ConcurrentHashMap<>();
    private final Map<UUID,Lease> leases=new HashMap<>();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->{Thread t=new Thread(r,"TerrainMesh-worker");t.setDaemon(true);return t;});
    public Job get(UUID id) {return jobs.get(id);}
    public synchronized Job reserve(UUID id,UUID world,Envelope bounds,boolean editing) {
        if(jobs.containsKey(id)) throw new IllegalArgumentException("You already have a queued or running job.");
        if(editing) {
            for(var lease:leases.values()) if(lease.world.equals(world) && lease.bounds.intersects(bounds)) throw new IllegalArgumentException("Another TerrainMesh edit overlaps this area.");
            leases.put(id,new Lease(world,new Envelope(bounds)));
        }
        Job j=new Job();jobs.put(id,j);return j;
    }
    public void execute(UUID id,Runnable work) {
        Job expected=jobs.get(id);
        try {worker.execute(()->{try{work.run();}finally{complete(id,expected);}});}
        catch(RejectedExecutionException ex) {release(id);throw new IllegalArgumentException("Terrain queue is full or shutting down.");}
    }
    private synchronized void release(UUID id) {jobs.remove(id);leases.remove(id);}
    public synchronized void complete(UUID id,Job expected) {if(jobs.remove(id,expected))leases.remove(id);}
    public boolean cancel(UUID id) {var j=jobs.get(id);if(j==null)return false;j.cancel.set(true);return true;}
    public void shutdown() {
        jobs.values().forEach(j->j.cancel.set(true));worker.shutdown();
        // Do not interrupt an EditSession while it is flushing. Plugin disable must not wait on the main thread.
    }
}
