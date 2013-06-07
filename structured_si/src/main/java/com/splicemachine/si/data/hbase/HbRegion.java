package com.splicemachine.si.data.hbase;

import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.regionserver.HRegion;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class HbRegion implements IHTable {
    final HRegion region;

    public HbRegion(HRegion region) {
        this.region = region;
    }

    @Override
    public void close() throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public Result get(HGet get) throws IOException {
        return region.get(get.getGet(), null);
    }

    @Override
    public Iterator<Result> scan(HScan scan) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void put(Put put) throws IOException {
        region.put(put);
    }

    @Override
    public void put(Put put, HRowLock rowLock) throws IOException {
        region.put(put, rowLock.regionRowLock);
    }

    @Override
    public void put(Put put, boolean durable) throws IOException {
        region.put(put, durable);
    }

    @Override
    public void put(List<Put> puts) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public boolean checkAndPut(byte[] family, byte[] qualifier, byte[] expectedValue, Put put) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void delete(Delete delete, HRowLock rowLock) throws IOException {
        region.delete(delete, rowLock.regionRowLock, true);
    }

    @Override
    public HRowLock lockRow(byte[] rowKey) throws IOException {
        final Integer lock = region.obtainRowLock(rowKey);
        if (lock == null) {
            throw new RuntimeException("Unable to obtain row lock on region of table " + region.getTableDesc().getNameAsString());
        }
        return new HRowLock(lock);
    }

    @Override
    public void unLockRow(HRowLock lock) throws IOException {
        region.releaseRowLock(lock.regionRowLock);
    }
}
