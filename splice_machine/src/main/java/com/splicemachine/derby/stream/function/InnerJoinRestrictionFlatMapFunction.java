package com.splicemachine.derby.stream.function;

import com.google.common.base.Optional;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.impl.sql.execute.operations.JoinOperation;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import com.splicemachine.derby.stream.iapi.OperationContext;
import scala.Tuple2;
import javax.annotation.concurrent.NotThreadSafe;
import java.util.Collections;
import java.util.Iterator;

/**
 * Created by jleach on 4/22/15.
 */
@NotThreadSafe
public class InnerJoinRestrictionFlatMapFunction<Op extends SpliceOperation> extends SpliceFlatMapFunction<Op,Iterator<Tuple2<LocatedRow,Optional<LocatedRow>>>,LocatedRow> {
    protected OuterJoinFunction<Op> outerJoin = null;
    protected JoinRestrictionPredicateFunction<Op> joinRestriction = null;
    protected JoinOperation op = null;
    protected Tuple2<LocatedRow, Optional<LocatedRow>> tuple = null;
    protected boolean initialized = false;
    public InnerJoinRestrictionFlatMapFunction() {
        super();
    }

    public InnerJoinRestrictionFlatMapFunction(OperationContext<Op> operationContext) {
        super(operationContext);
    }

    @Override
    public Iterable<LocatedRow> call(Iterator<Tuple2<LocatedRow, Optional<LocatedRow>>> tuple2Iterator) throws Exception {
        assert tuple2Iterator.hasNext():"InnerJoinRestrictionFlatMapFunction should always have at least one record in iterator";
        if (!initialized) { // Initialize the sub functions
            op = (JoinOperation) operationContext.getOperation();
            outerJoin = new OuterJoinFunction<>(operationContext);
            joinRestriction = new JoinRestrictionPredicateFunction(operationContext);
            initialized = true;
        }
        while (tuple2Iterator.hasNext()) {
            tuple = tuple2Iterator.next();
            LocatedRow locatedRow = outerJoin.call(tuple);
            if (joinRestriction.call(locatedRow)) // Has Row, return
                return Collections.singletonList(locatedRow);
        }
        return Collections.EMPTY_LIST;
    }
}