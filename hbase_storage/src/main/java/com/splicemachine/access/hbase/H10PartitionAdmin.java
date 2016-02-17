package com.splicemachine.access.hbase;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.splicemachine.access.api.PartitionAdmin;
import com.splicemachine.access.api.PartitionCreator;
import com.splicemachine.concurrent.Clock;
import com.splicemachine.si.constants.SIConstants;
import com.splicemachine.storage.*;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.regionserver.BloomType;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Scott Fines
 *         Date: 12/31/15
 */
public class H10PartitionAdmin implements PartitionAdmin{
    private final Admin admin;
    private final long splitSleepInterval;
    private final Clock timeKeeper;
    private final HBaseTableInfoFactory tableInfoFactory;
    private final PartitionInfoCache<TableName> partitionInfoCache;

    public H10PartitionAdmin(Admin admin,
                             long splitSleepInterval,
                             Clock timeKeeper,
                             HBaseTableInfoFactory tableInfoFactory,
                             PartitionInfoCache<TableName> partitionInfoCache
                             ){
        this.admin=admin;
        this.splitSleepInterval = splitSleepInterval;
        this.timeKeeper=timeKeeper;
        this.tableInfoFactory = tableInfoFactory;
        this.partitionInfoCache = partitionInfoCache;
    }

    @Override
    public PartitionCreator newPartition() throws IOException{
        //TODO -sf- configure this more carefully
        HColumnDescriptor snapshot = new HColumnDescriptor(SIConstants.DEFAULT_FAMILY_BYTES);
        snapshot.setMaxVersions(Integer.MAX_VALUE);
        snapshot.setCompressionType(Compression.Algorithm.NONE);
        snapshot.setInMemory(true);
        snapshot.setBlockCacheEnabled(true);
        snapshot.setBloomFilterType(BloomType.ROW);
        return new HPartitionCreator(tableInfoFactory,admin.getConnection(),timeKeeper,snapshot,partitionInfoCache);
    }

    @Override
    public void deleteTable(String tableName) throws IOException{
        admin.deleteTable(tableInfoFactory.getTableInfo(tableName));
    }

    @Override
    public void splitTable(String tableName,byte[]... splitPoints) throws IOException{
        TableName tableInfo=tableInfoFactory.getTableInfo(tableName);
        for(byte[] splitPoint:splitPoints){
            admin.split(tableInfo,splitPoint);
        }
        boolean isSplitting = true;
        while(isSplitting){
            isSplitting=false;
            try {
                List<HRegionInfo> regions = admin.getTableRegions(tableInfo);
                if(regions!=null){
                    for(HRegionInfo region:regions){
                        if(region.isSplit()){
                            isSplitting=true;
                            break;
                        }
                    }
                }else{
                    isSplitting=true;
                }

                timeKeeper.sleep(splitSleepInterval,TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new InterruptedIOException();
            }
        }
    }

    @Override
    public void close() throws IOException{
        admin.close();
    }

    @Override
    public Collection<PartitionServer> allServers() throws IOException{
        Collection<ServerName> servers=admin.getClusterStatus().getServers();
        return Collections2.transform(servers,new Function<ServerName, PartitionServer>(){
            @Override
            public PartitionServer apply(ServerName input){
                return new HServer(input,admin);
            }
        });
    }

    @Override
    public Iterable<? extends Partition> allPartitions(String tableName) throws IOException{
        TableName tn =tableInfoFactory.getTableInfo(tableName);
        List<HRegionInfo> tableRegions=admin.getTableRegions(tn);
        List<Partition> partitions=new ArrayList<>(tableRegions.size());
        Connection connection=admin.getConnection();
        Table table = connection.getTable(tn);
        for(HRegionInfo info : tableRegions){
            LazyPartitionServer owningServer=new LazyPartitionServer(connection,info,tn);
            partitions.add(new RangedClientPartition(connection,tn,table,info,owningServer,timeKeeper,partitionInfoCache));
        }
        return partitions;
    }
}
