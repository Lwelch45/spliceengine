/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.storage;

/**
 * @author Scott Fines
 *         Date: 12/15/15
 */
public interface DataScan extends Attributable{

    DataScan startKey(byte[] startKey);

    DataScan stopKey(byte[] stopKey);

    DataScan filter(DataFilter df);

    /**
     * Reverse the order in which this scan is operating.
     *
     * @return a scan which scans in reverse (i.e. descending order).
     */
    DataScan reverseOrder();

    boolean isDescendingScan();

    DataScan cacheRows(int rowsToCache);

    DataScan batchCells(int cellsToBatch);

    byte[] getStartKey();

    byte[] getStopKey();

    long highVersion();

    long lowVersion();

    DataFilter getFilter();

    void setTimeRange(long lowVersion,long highVersion);

    void returnAllVersions();


}
