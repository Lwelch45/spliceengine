package com.splicemachine.hbase;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.splicemachine.derby.utils.SpliceUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.client.coprocessor.Batch;
import org.apache.hadoop.hbase.ipc.CoprocessorProtocol;
import org.apache.hadoop.hbase.ipc.ExecRPCInvoker;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Pair;
import org.apache.hadoop.hbase.util.Writables;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.*;

/**
 * Thread-safe HTable implementation.
 *
 * @author Scott Fines
 * Created on: 3/8/13
 */
@SuppressWarnings({"unchecked", "ConstantConditions"})
public class SafeTable implements SpliceTable{
    private static final Logger LOG = Logger.getLogger(SafeTable.class);
    /*
     * Buffer management entries.
     *
     * putWriter is the Buffer manager entity for the Put buffer, while
     * deleteWriter manages the Delete Buffer
     */
    private final ThreadSafeCallBuffer.Listener<Put> putWriter = new ThreadSafeCallBuffer.Listener<Put>() {
        @Override
        public long heapSize(Put element) {
            validatePut(element);
            return element.heapSize();
        }

        @Override
        public void bufferFlushed(List<Put> entries)  throws IOException{
            flush(entries);
        }
    };
    private final ThreadSafeCallBuffer.Listener<Delete> deleteWriter = new ThreadSafeCallBuffer.Listener<Delete>() {
        @Override
        public long heapSize(Delete element) {
            return 1l; // "heap" actually means number of rows--we're going to ignore it anyway
        }

        @Override
        public void bufferFlushed(List<Delete> entries)  throws IOException{
            flush(entries);
        }
    };

    private final HConnection connection;
    private final Configuration configuration;
    private final ExecutorService multiPool;
    private final CallBuffer<Put> putBuffer;
    private final CallBuffer<Delete> deleteBuffer;
    private final byte[] tableName;
    private final long maxScannerResultSize;
    private final int scannerCacheSize;
    private final long scannerTimeout;
    private final int maxKeyValueSize;
    private final int operationTimeout;
    private final boolean cleanupOnClose;
    private volatile boolean autoFlush;
    private volatile boolean closed = false;

    private SafeTable(HConnection connection,
                     Configuration configuration,
                     ExecutorService multiPool,
                     byte[] tableName,
                     long writeBufferSizeBytes,
                     int maxWriteBufferEntries,
                     long maxScannerResultSize,
                     long scannerTimeout,
                     int scannerCacheSize,
                     int operationTimeout,
                     int maxKeyValueSize,
                     boolean autoFlush,
                     boolean clearBufferOnFail) {
        this.connection = connection;
        this.configuration = configuration;
        this.multiPool = multiPool;
        this.tableName = tableName;

        this.maxScannerResultSize = maxScannerResultSize;
        this.scannerCacheSize = scannerCacheSize;
        this.scannerTimeout = scannerTimeout;
        this.autoFlush = autoFlush;
        this.maxKeyValueSize = maxKeyValueSize;
        this.operationTimeout = operationTimeout;
        this.cleanupOnClose = true;

        putBuffer = new ThreadSafeCallBuffer<Put>(putWriter,writeBufferSizeBytes,maxWriteBufferEntries);
        deleteBuffer = new ThreadSafeCallBuffer<Delete>(deleteWriter,-1l,maxWriteBufferEntries);
    }

    public static SafeTable create(Configuration conf, byte[] tableName) throws IOException{
        /*
         * Since the whole point of this class is to use write buffers for performance, seems kind
         * of silly to NOT buffer, so the default is to buffer
         */
        return create(conf,tableName,true);
    }
    public static SafeTable create(Configuration conf, final byte[] tableName,boolean autoFlush) throws IOException{
        Preconditions.checkNotNull(conf,"No Configuration specified");
        Preconditions.checkNotNull(tableName,"No Table name specified");
        HConnection connection;

        connection = SpliceUtils.getHConnection();
        int maxThreads = conf.getInt("hbase.htable.threads.max",Integer.MAX_VALUE);
        if(maxThreads==0){
            maxThreads = 1;
        }
        long keepAliveTime = conf.getLong("hbase.htable.threads.keepalivetime",60);

        ThreadFactory factory = new ThreadFactoryBuilder()
                .setNameFormat("safetable-poolthread-%d")
                .setDaemon(true)
                .setPriority(Thread.NORM_PRIORITY).build();
        ExecutorService pool = new ThreadPoolExecutor(1,maxThreads,keepAliveTime,
                TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                factory);

        long writeBufferSize = conf.getLong("hbase.client.write.buffer",2097152);
        int scannerCaching = conf.getInt("hbase.client.scanner.caching",1);
        long maxScannerResultSize = conf.getLong(
                HConstants.HBASE_CLIENT_SCANNER_MAX_RESULT_SIZE_KEY,
                HConstants.DEFAULT_HBASE_CLIENT_SCANNER_MAX_RESULT_SIZE);
        int maxKeyValueSize = conf.getInt("hbase.client.keyvalue.maxsize",-1);

        int maxBufferEntries = conf.getInt("hbase.client.write.buffer.maxentries",-1);
        long scannerTimeout = conf.getLong(
                HConstants.HBASE_REGIONSERVER_LEASE_PERIOD_KEY,
                HConstants.DEFAULT_HBASE_REGIONSERVER_LEASE_PERIOD);

        int operationTimeout =
                HTableDescriptor.isMetaTable(tableName)? HConstants.DEFAULT_HBASE_CLIENT_OPERATION_TIMEOUT
                        :conf.getInt(HConstants.HBASE_CLIENT_OPERATION_TIMEOUT,
                        HConstants.DEFAULT_HBASE_CLIENT_OPERATION_TIMEOUT);

        connection.locateRegion(tableName,HConstants.EMPTY_START_ROW);
        return new SafeTable(connection,conf,pool,tableName,
                writeBufferSize,maxBufferEntries,maxScannerResultSize,scannerTimeout,scannerCaching,
                operationTimeout,maxKeyValueSize,autoFlush, true);
    }

    @Override
    public byte[] getTableName() {
        return tableName;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public HTableDescriptor getTableDescriptor() throws IOException {
        return new ImmutableHTableDescriptor(
                this.connection.getHTableDescriptor(this.tableName));
    }

    @Override
    public boolean exists(final Get get) throws IOException {
        return connection.getRegionServerWithRetries(
                new ServerCallable<Boolean>(connection, tableName, get.getRow(), operationTimeout) {
                    @Override
                    public Boolean call() throws Exception {
                        return server.exists(location.getRegionInfo().getRegionName(), get);
                    }
                }
        );
    }

    @Override
    public void batch(List<Row> actions, Object[] results) throws IOException, InterruptedException {
        connection.processBatch(actions,tableName,multiPool,results);
    }

    @Override
    public Object[] batch(List<Row> actions) throws IOException, InterruptedException {
        Object[] results = new Object[actions.size()];
        connection.processBatch(actions,tableName,multiPool,results);
        return results;
    }

    @Override
    public Result get(final Get get) throws IOException {
        return connection.getRegionServerWithRetries(
                new ServerCallable<Result>(connection,tableName,get.getRow(),operationTimeout) {
            @Override
            public Result call() throws Exception {
                return server.get(location.getRegionInfo().getRegionName(),get);
            }
        });
    }

    @Override
    public Result[] get(List<Get> gets) throws IOException {
        try{
            Object[] r1 = batch((List)gets);

            Result [] results = new Result[r1.length];
            int i=0;
            for(Object o:r1){
                results[i++] = (Result)o;
            }
            return results;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Result getRowOrBefore(byte[] row, final byte[] family) throws IOException {
        return connection.getRegionServerWithRetries(
                new ServerCallable<Result>(connection,tableName,row,operationTimeout) {
            @Override
            public Result call() throws Exception {
                return server.getClosestRowBefore(location.getRegionInfo().getRegionName(),row,family);
            }
        });
    }

    @Override
    public ResultScanner getScanner(Scan scan) throws IOException {
        ClientScanner s = new ClientScanner(tableName,connection,scan,
                maxScannerResultSize,scannerTimeout);
        s.initialize();
        return s;
    }

    @Override
    public ResultScanner getScanner(byte[] family) throws IOException {
        Scan scan = new Scan();
        scan.addFamily(family);
        return getScanner(scan);
    }

    @Override
    public ResultScanner getScanner(byte[] family, byte[] qualifier) throws IOException {
        Scan scan = new Scan();
        scan.addColumn(family,qualifier);
        return getScanner(scan);
    }

    @Override
    public void put(Put put) throws IOException {
        try {
            putBuffer.add(put);
        } catch (Exception e) {
            throw (IOException)e;
        }
        if(autoFlush)flushCommits();
    }

    @Override
    public void put(List<Put> puts) throws IOException {
        try {
            putBuffer.addAll(puts);
        } catch (Exception e) {
            throw (IOException)e;
        }
        if(autoFlush) flushCommits();
    }

    private void validatePut(Put put) {
        Preconditions.checkArgument(!put.isEmpty(),"No Columns to insert");
        if(maxKeyValueSize >0){
            for(List<KeyValue> list: put.getFamilyMap().values()){
                for(KeyValue kv:list){
                    if(kv.getLength() > maxKeyValueSize){
                        throw new IllegalArgumentException("KeyValue size too large");
                    }
                }
            }
        }
    }

    @Override
    public boolean checkAndPut(byte[] row, final byte[] family, final byte[] qualifier, final byte[] value, final Put put) throws IOException {
        return connection.getRegionServerWithRetries(
                new ServerCallable<Boolean>(connection,tableName,row,operationTimeout) {
                    @Override
                    public Boolean call() throws Exception {
                        return server.checkAndPut(location.getRegionInfo().getRegionName(),
                                row,family,qualifier,value,put)?Boolean.TRUE: Boolean.FALSE;
                    }
                }
        );
    }

    @Override
    public void delete(final Delete delete) throws IOException {
        try {
            deleteBuffer.add(delete);
        } catch (Exception e) {
            throw (IOException)e;
        }
        if(autoFlush) flushCommits();

    }

    @Override
    public void delete(List<Delete> deletes) throws IOException {
        try{
            deleteBuffer.addAll(deletes);
        }catch (Exception e) {
            throw (IOException)e;
        }
        if(autoFlush) flushCommits();
    }

    @Override
    public boolean checkAndDelete(byte[] row, final byte[] family,
                                  final byte[] qualifier, final byte[] value,
                                  final Delete delete) throws IOException {
        return connection.getRegionServerWithRetries(
                new ServerCallable<Boolean>(connection,tableName,row,operationTimeout) {
                    @Override
                    public Boolean call() throws Exception {
                        return server.checkAndDelete(
                                location.getRegionInfo().getRegionName(),
                                row,family,qualifier,value,delete)? Boolean.TRUE: Boolean.FALSE;
                    }
                }
        );
    }

    @Override
    public Result increment(final Increment increment) throws IOException {
        Preconditions.checkArgument(increment.hasFamilies(),"No columns specified for increment");

        return connection.getRegionServerWithRetries(
                new ServerCallable<Result>(connection,tableName,increment.getRow(),operationTimeout) {
                    @Override
                    public Result call() throws Exception {
                        return server.increment(
                                location.getRegionInfo().getRegionName(),increment);
                    }
                }
        );
    }

    @Override
    public long incrementColumnValue(byte[] row,
                                     byte[] family, byte[] qualifier,
                                     long amount) throws IOException {
        return incrementColumnValue(row,family,qualifier,amount,true);
    }

    @Override
    public long incrementColumnValue(byte[] row,
                                     final byte[] family, final byte[] qualifier,
                                     final long amount, final boolean writeToWAL) throws IOException {
        Preconditions.checkNotNull(row,"row is null");
        Preconditions.checkNotNull(family,"Column family is null");

        return connection.getRegionServerWithRetries(
                new ServerCallable<Long>(connection,tableName,row,operationTimeout) {
                    @Override
                    public Long call() throws Exception {
                        return server.incrementColumnValue(
                                location.getRegionInfo().getRegionName(),row,family,
                                qualifier,amount,writeToWAL);
                    }
                }
        );
    }

    @Override
    public boolean isAutoFlush() {
        return autoFlush;
    }

    @Override
    public void flushCommits() throws IOException {
        try {
            putBuffer.flushBuffer();
            deleteBuffer.flushBuffer();
        } catch (Exception e) {
            throw (IOException)e; //we know that the type will always be IOException here
        }
    }



    @Override
    public void close() throws IOException {
        if(closed) return; //already closed, do nothing
        flushCommits();
        if(cleanupOnClose){
            this.multiPool.shutdown();
//            synchronized (this){
//                if(this.connection!=null){
//                    this.connection.close();
//                }
//            }
        }
        this.closed= true;
    }

    @Override
    public RowLock lockRow(byte[] row) throws IOException {
        return connection.getRegionServerWithRetries(
                new ServerCallable<RowLock>(connection,tableName,row,operationTimeout) {
                    @Override
                    public RowLock call() throws Exception {
                        long lockId =
                                server.lockRow(location.getRegionInfo().getRegionName(),row);
                        return new RowLock(row,lockId);
                    }
                }
        );
    }

    @Override
    public void unlockRow(final RowLock rl) throws IOException {
        connection.getRegionServerWithRetries(
                new ServerCallable<Boolean>(connection,tableName,rl.getRow(),operationTimeout){

                    @Override
                    public Boolean call() throws Exception {
                        server.unlockRow(location.getRegionInfo().getRegionName(),rl.getLockId());
                        return Boolean.TRUE;
                    }
                }
        );
    }

    @Override
    public <T extends CoprocessorProtocol> T coprocessorProxy(Class<T> protocol, byte[] row) {
        return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(),
                new Class[]{protocol},
                new ExecRPCInvoker(configuration,
                        connection,
                        protocol,
                        tableName,
                        row));
    }

    @Override
    public <T extends CoprocessorProtocol, R> Map<byte[], R> coprocessorExec(Class<T> protocol,
                                                                             byte[] startKey, byte[] endKey,
                                                                             Batch.Call<T, R> callable) throws Throwable {
        final Map<byte[],R> results = new ConcurrentSkipListMap<byte[], R>(
                Bytes.BYTES_COMPARATOR);
        coprocessorExec(protocol, startKey,endKey,callable,new Batch.Callback<R>(){

            @Override
            public void update(byte[] region, byte[] row, R result) {
                results.put(region,result);
            }
        });
        return results;
    }

    @Override
    public <T extends CoprocessorProtocol, R> void coprocessorExec(Class<T> protocol,
                                                                   byte[] startKey, byte[] endKey,
                                                                   Batch.Call<T, R> callable,
                                                                   Batch.Callback<R> callback) throws Throwable {
        List<byte[]> keys = getStartKeysInRange(startKey,endKey);
        connection.processExecs(protocol,keys,tableName,multiPool, callable,callback);
    }

/***************************************************************************************************/
    /*private helper methods*/

    private void flush(List<? extends Row> ops) throws IOException{
        Object[] results = new Object[ops.size()];
        try{
            this.connection.processBatch(ops,tableName,multiPool,results);
        }catch(InterruptedException ie){
            throw new IOException(ie);
        }
    }

    private List<byte[]> getStartKeysInRange(byte[] start, byte[] end)
            throws IOException {
        Pair<byte[][],byte[][]> startEndKeys = getStartEndKeys();
        byte[][] startKeys = startEndKeys.getFirst();
        byte[][] endKeys = startEndKeys.getSecond();

        if (start == null) {
            start = HConstants.EMPTY_START_ROW;
        }
        if (end == null) {
            end = HConstants.EMPTY_END_ROW;
        }

        List<byte[]> rangeKeys = new ArrayList<byte[]>();
        for (int i=0; i<startKeys.length; i++) {
            if (Bytes.compareTo(start, startKeys[i]) >= 0 ) {
                if (Bytes.equals(endKeys[i], HConstants.EMPTY_END_ROW) ||
                        Bytes.compareTo(start, endKeys[i]) < 0) {
                    rangeKeys.add(start);
                }
            } else if (Bytes.equals(end, HConstants.EMPTY_END_ROW) ||
                    Bytes.compareTo(startKeys[i], end) <= 0) {
                rangeKeys.add(startKeys[i]);
            } else {
                break; // past stop
            }
        }

        return rangeKeys;
    }

    private Pair<byte[][],byte[][]> getStartEndKeys() throws IOException {
        final List<byte[]> startKeyList = new ArrayList<byte[]>();
        final List<byte[]> endKeyList = new ArrayList<byte[]>();
        MetaScanner.MetaScannerVisitor visitor = new MetaScanner.MetaScannerVisitor() {
            public boolean processRow(Result rowResult) throws IOException {
                byte [] bytes = rowResult.getValue(HConstants.CATALOG_FAMILY,
                        HConstants.REGIONINFO_QUALIFIER);
                if (bytes == null) {
                    LOG.warn("Null " + HConstants.REGIONINFO_QUALIFIER_STR +
                            " cell in " + rowResult);
                    return true;
                }
                HRegionInfo info = Writables.getHRegionInfo(bytes);
                if (Bytes.equals(info.getTableName(), getTableName())) {
                    if (!(info.isOffline() || info.isSplit())) {
                        startKeyList.add(info.getStartKey());
                        endKeyList.add(info.getEndKey());
                    }
                }
                return true;
            }
        };
        MetaScanner.metaScan(configuration, visitor, this.tableName);
        return new Pair<byte [][], byte [][]>(
                startKeyList.toArray(new byte[startKeyList.size()][]),
                endKeyList.toArray(new byte[endKeyList.size()][]));
    }

    private HConnection getConnection() {
        return connection;
    }


}
