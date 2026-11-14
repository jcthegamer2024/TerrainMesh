package org.sylvarion.terrainmesh.generation;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Envelope;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TerrainJobManagerTest {
    @Test void overlappingEditsAreRejectedUntilLeaseReleased() {
        var jobs=new TerrainJobManager();UUID world=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        try {
            var first=jobs.reserve(a,world,new Envelope(0,10,0,10),true);
            assertThrows(IllegalArgumentException.class,()->jobs.reserve(b,world,new Envelope(5,15,5,15),true));
            assertTrue(jobs.cancel(a));
            // A request to cancel does not release a running edit's lease.
            assertThrows(IllegalArgumentException.class,()->jobs.reserve(b,world,new Envelope(5,15,5,15),true));
            jobs.complete(a,first);assertNotNull(jobs.reserve(b,world,new Envelope(5,15,5,15),true));
        }finally{jobs.shutdown();}
    }
    @Test void staleCompletionCannotReleaseANewerJob() {
        var jobs=new TerrainJobManager();UUID id=UUID.randomUUID(),world=UUID.randomUUID();
        try {
            var old=jobs.reserve(id,world,new Envelope(0,10,0,10),false);jobs.complete(id,old);
            var next=jobs.reserve(id,world,new Envelope(0,10,0,10),true);jobs.complete(id,old);
            assertSame(next,jobs.get(id));
        }finally{jobs.shutdown();}
    }
}
