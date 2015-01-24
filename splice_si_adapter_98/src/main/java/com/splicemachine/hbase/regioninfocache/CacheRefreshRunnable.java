package com.splicemachine.hbase.regioninfocache;

import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.MetaScanAction;
import org.apache.hadoop.hbase.regionserver.RegionServerStoppedException;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicLong;

import static com.splicemachine.utils.SpliceLogUtils.*;

/**
 * Task scheduled to periodically refresh cache.
 */
class CacheRefreshRunnable implements Runnable {

    /* Intentionally using same logger for all classes in this package. */
    private static final Logger LOG = Logger.getLogger(HBaseRegionCache.class);

    private final Map<byte[], SortedSet<Pair<HRegionInfo, ServerName>>> regionCache;
    private final AtomicLong cacheUpdatedTimestamp;
    private final byte[] updateTableName;

    CacheRefreshRunnable(Map<byte[], SortedSet<Pair<HRegionInfo, ServerName>>> regionCache, AtomicLong cacheUpdatedTimestamp, byte[] updateTableName) {
        this.regionCache = regionCache;
        this.cacheUpdatedTimestamp = cacheUpdatedTimestamp;
        this.updateTableName = updateTableName;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();
        debug(LOG, "Refreshing region cache for table = %s", (updateTableName == null ? "ALL" : Bytes.toString(updateTableName)));
        doRun(startTime,10);

        /* Only update the refresh timestamp if we are loading for all tables */
        if (updateTableName == null) {
            cacheUpdatedTimestamp.set(System.currentTimeMillis());
        }
    }

    private void doRun(long startTime,int iteration) {
        if(iteration<0) {
            info(LOG,"Giving up refresh after many attempts");
            return;
        }
        RegionMetaScannerVisitor visitor = new RegionMetaScannerVisitor(updateTableName);
        try {
            MetaScanAction.metaScan(visitor,TableName.valueOf(updateTableName));
            Map<byte[], SortedSet<Pair<HRegionInfo, ServerName>>> newRegionInfoMap = visitor.getRegionPairMap();
            regionCache.putAll(newRegionInfoMap);
            debug(LOG, "updated %s cache entries in %s ms ", newRegionInfoMap.size(), System.currentTimeMillis() - startTime);
        } catch (IOException e) {
            if (e instanceof RegionServerStoppedException) {
                HBaseRegionCache.getInstance().shutdown();
                info(LOG, "The region cache is shutting down as the server has stopped");
            } else if(e instanceof NotServingRegionException){
                debug(LOG, "META region is not currently available, retrying");
                doRun(startTime,iteration-1);
            } else{
                error(LOG, "Unable to update region cache", e);
            }
        }
    }

}
