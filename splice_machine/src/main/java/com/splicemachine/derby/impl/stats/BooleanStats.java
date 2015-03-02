package com.splicemachine.derby.impl.stats;

import com.google.common.base.Function;
import com.splicemachine.stats.BooleanColumnStatistics;
import com.splicemachine.stats.frequency.BooleanFrequencyEstimate;
import com.splicemachine.stats.frequency.BooleanFrequentElements;
import com.splicemachine.stats.frequency.FrequencyEstimate;
import com.splicemachine.stats.frequency.FrequentElements;
import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.iapi.types.DataValueDescriptor;
import org.apache.derby.iapi.types.SQLBoolean;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 2/27/15
 */
public class BooleanStats extends BaseDvdStatistics {
    private BooleanColumnStatistics baseStats;

    public BooleanStats() { }

    public BooleanStats(BooleanColumnStatistics baseStats) {
        super(baseStats);
        this.baseStats = baseStats;
    }

    @Override
    public FrequentElements<DataValueDescriptor> topK() {
        return new BooleanFreqs((BooleanFrequentElements) baseStats.topK());
    }

    @Override
    public DataValueDescriptor minValue() {
        if(baseStats.trueCount().count()>0)return SQLBoolean.trueTruthValue();
        else if(baseStats.falseCount().count()>0) return SQLBoolean.falseTruthValue();
        return SQLBoolean.unknownTruthValue(); //should never happen, but just in case
    }

    @Override
    public DataValueDescriptor maxValue() {
        if(baseStats.falseCount().count()>0)return SQLBoolean.falseTruthValue();
        else if(baseStats.trueCount().count()>0) return SQLBoolean.trueTruthValue();
        return SQLBoolean.unknownTruthValue(); //should never happen, but just in case
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        BooleanColumnStatistics.encoder().encode(baseStats,out);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        baseStats = BooleanColumnStatistics.encoder().decode(in);
    }

    /* ****************************************************************************************************************/
    /*private helper methods*/
    private class BooleanFreqs implements FrequentElements<DataValueDescriptor> {
        private BooleanFrequentElements frequentElements;

        public BooleanFreqs(BooleanFrequentElements freqs) {
            this.frequentElements = freqs;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<? extends FrequencyEstimate<DataValueDescriptor>> allFrequentElements() {
            return convert((Set<BooleanFrequencyEstimate>)frequentElements.allFrequentElements());
        }

        @Override
        public FrequencyEstimate<? extends DataValueDescriptor> equal(DataValueDescriptor element) {
            try {
                return new BooleanFreq(frequentElements.equals(element.getBoolean()));
            } catch (StandardException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<? extends FrequencyEstimate<DataValueDescriptor>> frequentElementsBetween(
                DataValueDescriptor start, DataValueDescriptor stop, boolean includeStart, boolean includeStop) {
            try {
                Set<BooleanFrequencyEstimate> baseEstimate;
                if (start == null || start.isNull()) {
                    if (stop == null || stop.isNull()) {
                        //get everything
                        baseEstimate = (Set<BooleanFrequencyEstimate>) frequentElements.allFrequentElements();
                    } else {
                        baseEstimate =  (Set<BooleanFrequencyEstimate>)frequentElements.frequentElementsBetween(
                                Boolean.TRUE,stop.getBoolean(),
                                true,includeStop);
                    }
                }else if(stop==null||stop.isNull()) {
                    baseEstimate = (Set<BooleanFrequencyEstimate>)frequentElements.frequentElementsBetween(
                            start.getBoolean(), Boolean.FALSE,
                            includeStart, true);
                }else{
                    baseEstimate = (Set<BooleanFrequencyEstimate>)frequentElements.frequentElementsBetween(
                            start.getBoolean(),start.getBoolean(),
                            includeStart,includeStop);
                }
                return convert(baseEstimate);

            }catch(StandardException se){
                throw new RuntimeException(se); //shouldn't happen
            }
        }

        @Override
        public FrequentElements<DataValueDescriptor> merge(FrequentElements<DataValueDescriptor> other) {
            assert other instanceof BooleanFreqs : "Cannot merge FrequentElements of type " + other.getClass();
            frequentElements = (BooleanFrequentElements)frequentElements.merge(((BooleanFreqs) other).frequentElements);
            return this;
        }

        private Set<? extends FrequencyEstimate<DataValueDescriptor>> convert(Set<BooleanFrequencyEstimate> other) {
            return new ConvertingSetView<>(other,conversionFunction);
        }
    }

    private static class BooleanFreq implements FrequencyEstimate<DataValueDescriptor> {
        private BooleanFrequencyEstimate baseEstimate;

        public BooleanFreq(BooleanFrequencyEstimate intFrequencyEstimate) {
            this.baseEstimate = intFrequencyEstimate;
        }

        @Override public DataValueDescriptor getValue() {
            return SQLBoolean.truthValue(baseEstimate.value());
        }
        @Override public long count() { return baseEstimate.count(); }
        @Override public long error() { return baseEstimate.error(); }

        @Override
        public FrequencyEstimate<DataValueDescriptor> merge(FrequencyEstimate<DataValueDescriptor> other) {
            assert other instanceof BooleanFreq: "Cannot merge FrequencyEstimate of type "+ other.getClass();
            baseEstimate = (BooleanFrequencyEstimate)baseEstimate.merge(((BooleanFreq) other).baseEstimate);
            return this;
        }
    }

    private static final Function<BooleanFrequencyEstimate,FrequencyEstimate<DataValueDescriptor>> conversionFunction
            = new Function<BooleanFrequencyEstimate, FrequencyEstimate<DataValueDescriptor>>() {
        @Override
        public FrequencyEstimate<DataValueDescriptor> apply(BooleanFrequencyEstimate intFrequencyEstimate) {
            return new BooleanFreq(intFrequencyEstimate);
        }
    };
}