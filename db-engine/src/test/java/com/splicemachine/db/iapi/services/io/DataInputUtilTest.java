/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.splicemachine.db.iapi.services.io;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test case for DataInputUtil.
 */
public class DataInputUtilTest extends TestCase {

    public void testSkipFully() throws IOException{
        int length = 1024;

        DataInput di = new DataInputStream(
                new ByteArrayInputStream(new byte[length]));
        DataInputUtil.skipFully(di, length);
        try {
            di.readByte();
            fail("Should have met EOF!");
        } catch (EOFException e) {
            assertTrue(true);
        }

        di = new DataInputStream(new ByteArrayInputStream(new byte[length]));
        DataInputUtil.skipFully(di, length - 1);
        di.readByte();
        try {
            di.readByte();
            fail("Should have met EOF!");
        } catch (EOFException e) {
            assertTrue(true);
        }
    }

}