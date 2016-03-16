package com.splicemachine.si.impl.driver;

import com.splicemachine.access.api.DistributedFileSystem;
import com.splicemachine.access.api.SConfiguration;
import com.splicemachine.access.api.PartitionFactory;
import com.splicemachine.concurrent.Clock;
import com.splicemachine.si.api.SIConfigurations;
import com.splicemachine.si.api.data.ExceptionFactory;
import com.splicemachine.si.api.data.OperationFactory;
import com.splicemachine.si.api.data.OperationStatusFactory;
import com.splicemachine.si.api.data.TxnOperationFactory;
import com.splicemachine.si.api.filter.TransactionReadController;
import com.splicemachine.si.api.readresolve.AsyncReadResolver;
import com.splicemachine.si.api.readresolve.KeyedReadResolver;
import com.splicemachine.si.api.readresolve.ReadResolver;
import com.splicemachine.si.api.readresolve.RollForward;
import com.splicemachine.si.api.server.TransactionalRegion;
import com.splicemachine.si.api.server.Transactor;
import com.splicemachine.si.api.txn.TxnLifecycleManager;
import com.splicemachine.si.api.txn.TxnStore;
import com.splicemachine.si.api.txn.TxnSupplier;
import com.splicemachine.si.impl.ClientTxnLifecycleManager;
import com.splicemachine.si.impl.TxnRegion;
import com.splicemachine.si.impl.readresolve.NoOpReadResolver;
import com.splicemachine.si.impl.rollforward.NoopRollForward;
import com.splicemachine.si.impl.rollforward.RollForwardStatus;
import com.splicemachine.si.impl.server.SITransactor;
import com.splicemachine.si.impl.txn.SITransactionReadController;
import com.splicemachine.storage.DataFilterFactory;
import com.splicemachine.storage.Partition;
import com.splicemachine.storage.PartitionInfoCache;
import com.splicemachine.timestamp.api.TimestampSource;
import com.splicemachine.utils.GreenLight;

public class SIDriver {
    private static volatile SIDriver INSTANCE;

    public static SIDriver driver(){ return INSTANCE;}

    public static SIDriver loadDriver(SIEnvironment env){
        SIDriver siDriver=new SIDriver(env);
        INSTANCE =siDriver;
        return siDriver;
    }

    private final SITransactionReadController readController;
    private final PartitionFactory tableFactory;
    private final ExceptionFactory exceptionFactory;
    private final SConfiguration config;
    private final TxnStore txnStore;
    private final OperationStatusFactory operationStatusFactory;
    private final TimestampSource timestampSource;
    private final TxnSupplier txnSupplier;
    private final Transactor transactor;
    private final TxnOperationFactory txnOpFactory;
    private final RollForward rollForward;
    private final TxnLifecycleManager lifecycleManager;
    private final DataFilterFactory filterFactory;
    private final Clock clock;
    private final AsyncReadResolver readResolver;
    private final DistributedFileSystem fileSystem;
    private final OperationFactory baseOpFactory;
    private final PartitionInfoCache partitionInfoCache;

    public SIDriver(SIEnvironment env){
        this.tableFactory = env.tableFactory();
        this.exceptionFactory = env.exceptionFactory();
        this.config = env.configuration();
        this.txnStore = env.txnStore();
        this.operationStatusFactory = env.statusFactory();
        this.timestampSource = env.timestampSource();
        this.txnSupplier = env.txnSupplier();
        this.txnOpFactory = env.operationFactory();
        this.rollForward = env.rollForward();
        this.filterFactory = env.filterFactory();
        this.clock = env.systemClock();
        this.partitionInfoCache = env.partitionInfoCache();

        //noinspection unchecked
        this.transactor = new SITransactor(
                this.txnSupplier,
                this.txnOpFactory,
                env.baseOperationFactory(),
                this.operationStatusFactory,
                this.exceptionFactory);
        ClientTxnLifecycleManager clientTxnLifecycleManager=new ClientTxnLifecycleManager(this.timestampSource,env.exceptionFactory());
        clientTxnLifecycleManager.setTxnStore(this.txnStore);
        clientTxnLifecycleManager.setKeepAliveScheduler(env.keepAliveScheduler());
        this.lifecycleManager =clientTxnLifecycleManager;
        readController = new SITransactionReadController(txnSupplier);
        readResolver = initializedReadResolver(config,env.keyedReadResolver());
        this.fileSystem = env.fileSystem();
        this.baseOpFactory = env.baseOperationFactory();
    }


    public TransactionReadController readController(){
        return readController;
    }

    public PartitionFactory getTableFactory(){
        return tableFactory;
    }

    public ExceptionFactory getExceptionFactory(){
        return exceptionFactory;
    }

    /**
     * @return the configuration specific to this architecture.
     */
    public SConfiguration getConfiguration(){
        return config;
    }

    public TxnStore getTxnStore() {
        return txnStore;
    }

    public TxnSupplier getTxnSupplier(){
        return txnSupplier;
    }

    public OperationStatusFactory getOperationStatusLib() {
        return operationStatusFactory;
    }

    public PartitionInfoCache getPartitionInfoCache() { return partitionInfoCache; }

    public TimestampSource getTimestampSource() {
        return timestampSource;
    }

    public Transactor getTransactor(){
        return transactor;
    }

    public TxnOperationFactory getOperationFactory(){
        return txnOpFactory;
    }

    public RollForward getRollForward(){
        return rollForward;
    }

    public ReadResolver getReadResolver(Partition basePartition){
        if(readResolver==null) return NoOpReadResolver.INSTANCE;
        else
        return readResolver.getResolver(basePartition,getRollForward());
    }

    public TxnLifecycleManager lifecycleManager(){
        return lifecycleManager;
    }

    public DataFilterFactory filterFactory(){
        return filterFactory;
    }

    public TransactionalRegion transactionalPartition(long conglomId,Partition basePartition){
        if(conglomId>=0){
            return new TxnRegion(basePartition,
                    getRollForward(),
                    getReadResolver(basePartition),
                    getTxnSupplier(),
                    getTransactor(),
                    getOperationFactory());
        }else{
            return new TxnRegion(basePartition,
                    NoopRollForward.INSTANCE,
                    NoOpReadResolver.INSTANCE,
                    getTxnSupplier(),
                    getTransactor(),
                    getOperationFactory());
        }
    }

    public Clock getClock(){
        return clock;
    }

    public DistributedFileSystem fileSystem(){
        return fileSystem;
    }

    public OperationFactory baseOperationFactory(){
        return baseOpFactory;
    }


    /* ****************************************************************************************************************/
    /*private helper methods*/
    private AsyncReadResolver initializedReadResolver(SConfiguration config,KeyedReadResolver keyedResolver){
        int maxThreads = config.getInt(SIConfigurations.READ_RESOLVER_THREADS);
        int bufferSize = config.getInt(SIConfigurations.READ_RESOLVER_QUEUE_SIZE);
        if(bufferSize<=0) return null;
        final AsyncReadResolver asyncReadResolver=new AsyncReadResolver(maxThreads,
                bufferSize,
                txnSupplier,
                new RollForwardStatus(),
                GreenLight.INSTANCE,keyedResolver);
        asyncReadResolver.start();
        return asyncReadResolver;
    }
}