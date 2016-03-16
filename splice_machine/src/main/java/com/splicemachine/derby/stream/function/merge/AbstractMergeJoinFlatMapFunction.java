package com.splicemachine.derby.stream.function.merge;

import com.google.common.base.Function;
import com.splicemachine.EngineDriver;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.impl.sql.execute.BaseActivation;
import com.splicemachine.db.impl.sql.execute.ValueRow;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import com.splicemachine.derby.impl.sql.execute.operations.MergeJoinOperation;
import com.splicemachine.derby.stream.function.SpliceFlatMapFunction;
import com.splicemachine.derby.stream.iapi.DataSetProcessor;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.stream.iterator.merge.AbstractMergeJoinIterator;
import com.splicemachine.derby.stream.utils.StreamUtils;
import org.sparkproject.guava.collect.Iterators;
import org.sparkproject.guava.collect.PeekingIterator;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Iterator;

/**
 * Created by jleach on 6/9/15.
 */
public abstract class AbstractMergeJoinFlatMapFunction extends SpliceFlatMapFunction<MergeJoinOperation,Iterator<LocatedRow>,LocatedRow> {
    boolean initialized;
    protected MergeJoinOperation mergeJoinOperation;

    public AbstractMergeJoinFlatMapFunction() {
        super();
    }

    public AbstractMergeJoinFlatMapFunction(OperationContext<MergeJoinOperation> operationContext) {
        super(operationContext);
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
    }

    @Override
    public Iterable<LocatedRow> call(Iterator<LocatedRow> locatedRows) throws Exception {
        PeekingIterator<LocatedRow> leftPeekingIterator = Iterators.peekingIterator(locatedRows);
        if (!initialized) {
            mergeJoinOperation = getOperation();
            initialized = true;
            if (!leftPeekingIterator.hasNext())
                return Collections.EMPTY_LIST;
            ((BaseActivation)mergeJoinOperation.getActivation()).setScanStartOverride(getScanStartOverride(leftPeekingIterator));
        }
        final SpliceOperation rightSide = mergeJoinOperation.getRightOperation();
        DataSetProcessor dsp =EngineDriver.driver().processorFactory().localProcessor(getOperation().getActivation(), rightSide);
        final Iterator<LocatedRow> rightIterator = Iterators.transform(rightSide.getDataSet(dsp).toLocalIterator(), new Function<LocatedRow, LocatedRow>() {
            @Override
            public LocatedRow apply(@Nullable LocatedRow locatedRow) {
                operationContext.recordJoinedRight();
                return locatedRow;
            }
        });
        ((BaseActivation)mergeJoinOperation.getActivation()).setScanStartOverride(null); // reset to null to avoid any side effects
        AbstractMergeJoinIterator iterator = createMergeJoinIterator(leftPeekingIterator,
                Iterators.peekingIterator(rightIterator),
                mergeJoinOperation.leftHashKeys, mergeJoinOperation.rightHashKeys,
                mergeJoinOperation, operationContext);
        iterator.registerCloseable(new Closeable() {
            @Override
            public void close() throws IOException {
                try {
                    rightSide.close();
                } catch (StandardException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        return iterator;
    }

    protected ExecRow getScanStartOverride(PeekingIterator<LocatedRow> leftPeekingIterator) throws StandardException{
        ExecRow firstHashRow = mergeJoinOperation.getKeyRow(leftPeekingIterator.peek().getRow());
        ExecRow startPosition = mergeJoinOperation.getRightResultSet().getStartPosition();
        return concatenate(startPosition, firstHashRow);
    }

    private ExecRow concatenate(ExecRow start, ExecRow hash) throws StandardException {
        int size = start!=null ? start.nColumns() : 0;
        int len = 1;
        int col = mergeJoinOperation.rightHashKeys[0];
        while(len <mergeJoinOperation.rightHashKeys.length && col+1 == mergeJoinOperation.rightHashKeys[len])
            col = mergeJoinOperation.rightHashKeys[len++];
        size += len;

        ExecRow v = new ValueRow(size);
        if (start != null) {
            for (int i = 1; i <= start.nColumns(); ++i) {
                v.setColumn(i, start.getColumn(i));
            }
        }
        int offset = start!=null ? start.nColumns():0;
        for (int i = 1; i <= len; ++i) {
            v.setColumn(offset + i, hash.getColumn(i));
        }
        return v;
    }

    protected abstract AbstractMergeJoinIterator createMergeJoinIterator(PeekingIterator<LocatedRow> leftPeekingIterator,
                                                                         PeekingIterator<LocatedRow> rightPeekingIterator,
                                                                         int[] leftHashKeys, int[] rightHashKeys, MergeJoinOperation mergeJoinOperation, OperationContext<MergeJoinOperation> operationContext);
}