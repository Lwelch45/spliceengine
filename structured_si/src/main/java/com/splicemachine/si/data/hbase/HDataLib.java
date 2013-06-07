package com.splicemachine.si.data.hbase;

import com.splicemachine.constants.bytes.BytesUtil;
import com.splicemachine.si.data.api.SDataLib;
import com.splicemachine.si.data.api.SRead;
import com.splicemachine.si.data.api.SRowLock;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.OperationWithAttributes;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class HDataLib implements SDataLib<byte[], Result, KeyValue, Put, Delete, HGet, HScan, HRead> {

    @Override
    public Result newResult(Object key, List keyValues) {
        return new Result(keyValues);
    }

    @Override
    public byte[] newRowKey(Object... args) {
        List<byte[]> bytes = new ArrayList<byte[]>();
        for (Object a : args) {
            bytes.add(convertToBytes(a, a.getClass()));
        }
        return BytesUtil.concat(bytes);
    }

    @Override
    public byte[] getResultKey(Result result) {
        return result.getRow();
    }

    @Override
    public byte[] getPutKey(Put put) {
        return put.getRow();
    }

    @Override
    public List<KeyValue> getResultColumn(Result result, byte[] family, byte[] qualifier) {
        return result.getColumn(family, qualifier);
    }

    @Override
    public byte[] getResultValue(Result result, byte[] family, byte[] qualifier) {
        return result.getValue(family, qualifier);
    }

    @Override
    public Map<byte[], byte[]> getResultFamilyMap(Result result, byte[] family) {
        return result.getFamilyMap(family);
    }

    @Override
    public List<KeyValue> listResult(Result result) {
        return result.list();
    }

    @Override
    public List<KeyValue> listPut(Put put) {
        final Map<byte[], List<KeyValue>> familyMap = put.getFamilyMap();
        List result = new ArrayList();
        for (List<KeyValue> subList : familyMap.values()) {
            result.addAll(subList);
        }
        return result;
    }

    @Override
    public KeyValue newKeyValue(byte[] rowKey, byte[] family, byte[] qualifier, Long timestamp, byte[] value) {
        return new KeyValue(rowKey, family, qualifier, timestamp, value);
    }

    @Override
    public byte[] getKeyValueRow(KeyValue keyValue) {
        return keyValue.getRow();
    }

    @Override
    public byte[] getKeyValueFamily(KeyValue keyValue) {
        return keyValue.getFamily();
    }

    @Override
    public byte[] getKeyValueQualifier(KeyValue keyValue) {
        return keyValue.getQualifier();
    }

    @Override
    public byte[] getKeyValueValue(KeyValue keyValue) {
        return keyValue.getValue();
    }

    @Override
    public long getKeyValueTimestamp(KeyValue keyValue) {
        return keyValue.getTimestamp();
    }

    @Override
    public byte[] encode(Object value) {
        return convertToBytes(value, value.getClass());
    }

    @Override
    public Object decode(byte[] value, Class type) {
        if (value == null) {
            return null;
        }
        final byte[] bytes = value;
        if (type.equals(Boolean.class)) {
            return Bytes.toBoolean(bytes);
        } else if (type.equals(Short.class)) {
            return Bytes.toShort(bytes);
        } else if (type.equals(Integer.class)) {
            return Bytes.toInt(bytes);
        } else if (type.equals(Long.class)) {
            return Bytes.toLong(bytes);
        } else if (type.equals(String.class)) {
            return Bytes.toString(bytes);
        }
        throw new RuntimeException("unsupported type conversion: " + type.getName());
    }

    @Override
    public boolean valuesEqual(byte[] value1, byte[] value2) {
        return Arrays.equals(value1, value2);
    }

    @Override
    public void addKeyValueToPut(Put put, byte[] family, byte[] qualifier, Long timestamp, byte[] value) {
        if (timestamp == null) {
            put.add(family, qualifier, value);
        } else {
            put.add(family, qualifier, timestamp, value);
        }
    }

    @Override
    public void addAttribute(Object operation, String attributeName, byte[] value) {
        OperationWithAttributes hOperation;
        if (operation instanceof OperationWithAttributes) {
            hOperation = (OperationWithAttributes) operation;
        } else {
            hOperation = ((IOperation) operation).getOperation();
        }
        hOperation.setAttribute(attributeName, value);
    }

    @Override
    public byte[] getAttribute(Object operation, String attributeName) {
        OperationWithAttributes hOperation;
        if (operation instanceof Put) {
            hOperation = (OperationWithAttributes) operation;
        } else {
            hOperation = ((IOperation) operation).getOperation();
        }
        return hOperation.getAttribute(attributeName);
    }

    @Override
    public Put newPut(byte[] key) {
        return new Put(key);
    }

    @Override
    public Put newPut(byte[] key, SRowLock lock) {
        return new Put(key, ((HRowLock) lock).lock);
    }

    @Override
    public HGet newGet(byte[] rowKey, List<byte[]> families, List<List<byte[]>> columns, Long effectiveTimestamp) {
        Get get = new Get(rowKey);
        if (families != null) {
            for (Object f : families) {
                get.addFamily((byte[]) f);
            }
        }
        if (columns != null) {
            for (List c : columns) {
                get.addColumn((byte[]) c.get(0), (byte[]) c.get(1));
            }
        }
        if (effectiveTimestamp != null) {
            try {
                get.setTimeRange(effectiveTimestamp, Long.MAX_VALUE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new HGet(get);
    }

    @Override
    public void setReadTimeRange(HRead read, long minTimestamp, long maxTimestamp) {
        read.setReadTimeRange(minTimestamp, maxTimestamp);
    }

    @Override
    public void setReadMaxVersions(HRead read) {
        read.setReadMaxVersions();
    }

    @Override
    public void setReadMaxVersions(HRead read, int max) {
        read.setReadMaxVersions(max);
    }

    @Override
    public void addFamilyToRead(HRead read, byte[] family) {
        read.addFamilyToRead(family);
    }

    @Override
    public void addFamilyToReadIfNeeded(HRead read, byte[] family) {
        read.addFamilyToReadIfNeeded(family);
    }

    @Override
    public HScan newScan(byte[] startRowKey, byte[] endRowKey, List<byte[]> families, List<List<byte[]>> columns, Long effectiveTimestamp) {
        Scan scan = new Scan();
        scan.setStartRow(startRowKey);
        scan.setStopRow(endRowKey);
        if (families != null) {
            for (Object f : families) {
                scan.addFamily((byte[]) f);
            }
        }
        if (columns != null) {
            for (List c : columns) {
                scan.addColumn((byte[]) c.get(0), (byte[]) c.get(1));
            }
        }
        if (effectiveTimestamp != null) {
            try {
                scan.setTimeRange(effectiveTimestamp, Long.MAX_VALUE);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new HScan(scan);
    }

    @Override
    public Delete newDelete(byte[] rowKey) {
        return new Delete(rowKey);
    }

    @Override
    public void addKeyValueToDelete(Delete delete, byte[] family, byte[] qualifier, long timestamp) {
        delete.deleteColumn(family, qualifier, timestamp);
    }

    static byte[] convertToBytes(Object value, Class clazz) {
        if (clazz == String.class) {
            return Bytes.toBytes((String) value);
        } else if (clazz == Integer.class) {
            return Bytes.toBytes((Integer) value);
        } else if (clazz == Short.class) {
            return Bytes.toBytes((Short) value);
        } else if (clazz == Long.class) {
            return Bytes.toBytes((Long) value);
        } else if (clazz == Boolean.class) {
            return Bytes.toBytes((Boolean) value);
        } else if (clazz == byte[].class) {
            return (byte[]) value;
        }
        throw new RuntimeException("Unsupported class " + clazz.getName() + " for " + value);
    }
}
