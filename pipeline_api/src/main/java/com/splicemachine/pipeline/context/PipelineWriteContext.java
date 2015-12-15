package com.splicemachine.pipeline.context;

import com.carrotsearch.hppc.ObjectObjectOpenHashMap;
import com.google.common.collect.Maps;
import com.splicemachine.access.api.ServerControl;
import com.splicemachine.access.util.CachedPartitionFactory;
import com.splicemachine.kvpair.KVPair;
import com.splicemachine.pipeline.callbuffer.CallBuffer;
import com.splicemachine.pipeline.api.Code;
import com.splicemachine.pipeline.writehandler.WriteHandler;
import com.splicemachine.pipeline.client.WriteResult;
import com.splicemachine.pipeline.writehandler.SharedCallBufferFactory;
import com.splicemachine.primitives.Bytes;
import com.splicemachine.si.api.server.TransactionalRegion;
import com.splicemachine.si.api.txn.TxnView;
import com.splicemachine.storage.Partition;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.log4j.Logger;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Scott Fines
 *         Created on: 4/30/13
 */
public class PipelineWriteContext implements WriteContext, Comparable<PipelineWriteContext> {
    private static final Logger LOG = Logger.getLogger(PipelineWriteContext.class);
    private static final AtomicInteger idGen = new AtomicInteger(0);

    private final Map<KVPair, WriteResult> resultsMap;
    private final TransactionalRegion rce;
    private final CachedPartitionFactory partitionFactory;
    private final TxnView txn;
    private final SharedCallBufferFactory indexSharedCallBuffer;
    private final int id = idGen.incrementAndGet();
    private final boolean skipIndexWrites;
    private final ServerControl env;
    private final WriteNode head;

    private WriteNode tail;

    public PipelineWriteContext(SharedCallBufferFactory indexSharedCallBuffer,
                                 CachedPartitionFactory partitionFactory,
                                 TxnView txn,
                                 TransactionalRegion rce,
                                 boolean skipIndexWrites,
                                 ServerControl env) {
        this.indexSharedCallBuffer = indexSharedCallBuffer;
        this.env = env;
        this.rce = rce;
        this.resultsMap = Maps.newIdentityHashMap();
        this.txn = txn;
        this.skipIndexWrites = skipIndexWrites;
        this.head = this.tail = new WriteNode(null, this);
        this.partitionFactory = partitionFactory;
    }

    public void addLast(WriteHandler handler) {
        SpliceLogUtils.debug(LOG, "addLast %s", handler);
        WriteNode newWriteNode = new WriteNode(handler, this);
        tail.setNext(newWriteNode);
        tail = newWriteNode;
    }

    @Override
    public void notRun(KVPair mutation) {
        resultsMap.put(mutation, WriteResult.notRun());
    }

    @Override
    public void sendUpstream(KVPair mutation) {
        head.sendUpstream(mutation);
    }

    @Override
    public void failed(KVPair put, WriteResult mutationResult) {
        resultsMap.put(put, mutationResult);
    }

    @Override
    public void success(KVPair put) {
        resultsMap.put(put, WriteResult.success());
    }

    @Override
    public void result(KVPair put, WriteResult result) {
        resultsMap.put(put, result);
    }

    @Override
    public void result(byte[] resultRowKey, WriteResult result) {
        for (KVPair kvPair : resultsMap.keySet()) {
            byte[] currentRowKey = kvPair.getRowKey();
            if (Arrays.equals(currentRowKey, resultRowKey)) {
                resultsMap.put(kvPair, result);
                return;
            }
        }
        throw new IllegalArgumentException("expected existing value in resultsMap");
    }

    @Override
    public Partition getRegion() {
        return txnRegion().unwrap();
    }

    @Override
    public Partition remotePartition(byte[] indexConglomBytes) throws IOException{
        return partitionFactory.getTable(Bytes.toString(indexConglomBytes));
    }

    @Override
    public CallBuffer<KVPair> getSharedWriteBuffer(byte[] conglomBytes,
                                                   ObjectObjectOpenHashMap<KVPair, KVPair> indexToMainMutationMap,
                                                   int maxSize, boolean useAsyncWriteBuffers, TxnView txn) throws Exception {
        assert indexSharedCallBuffer != null;
        return indexSharedCallBuffer.getWriteBuffer(conglomBytes, this, indexToMainMutationMap, maxSize, useAsyncWriteBuffers, txn);
    }

    @Override
    public void flush() throws IOException {
        if (env != null)
            env.ensureNetworkOpen();

        try {
            WriteNode next = head.getNext();
            while (next != null) {
                next.flush();
                next = next.getNext();
            }
            next = head.getNext();
            while (next != null) {
                next.close();
                next = next.getNext();
            }

        } finally {
            //clean up any outstanding table resources
            Collection<Partition> collection=partitionFactory.cachedPartitions();
            for (Partition table : collection) {
                try {
                    table.close();
                } catch (Exception e) {
                    //don't need to interrupt the finishing of this batch just because
                    //we got an error. Log it and move on
                    LOG.warn("Unable to clone table", e);
                }
            }
        }
    }

    @Override
    public boolean canRun(KVPair input) {
        WriteResult result = resultsMap.get(input);
        return result == null || result.getCode() == Code.SUCCESS;
    }

    @Override
    public TxnView getTxn() {
        return txn;
    }

    @Override
    public Map<KVPair, WriteResult> close() throws IOException {
        return resultsMap;
    }

    @Override
    public Map<KVPair, WriteResult> currentResults(){
        return resultsMap;
    }

    @Override
    public String toString() {
        return "PipelineWriteContext { region=" + rce.getRegionName() + " }";
    }

    @Override
    public int compareTo(PipelineWriteContext writeContext) {
        return this.id - writeContext.id;
    }

    @Override
    public boolean equals(Object o){
        if(o==this) return true;
        else if(!(o instanceof PipelineWriteContext)) return false;
        return compareTo((PipelineWriteContext)o)==0;
    }

    @Override
    public int hashCode(){
       return id;
    }

    @Override
    public ServerControl getCoprocessorEnvironment() {
        return env;
    }

    public TransactionalRegion getTransactionalRegion() {
        return rce;
    }

    @Override
    public boolean skipIndexWrites() {
        return this.skipIndexWrites;
    }

    @Override
    public TransactionalRegion txnRegion(){
        return rce;
    }
}