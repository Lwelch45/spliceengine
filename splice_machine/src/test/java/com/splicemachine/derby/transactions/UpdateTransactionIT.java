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

package com.splicemachine.derby.transactions;

import com.splicemachine.derby.test.framework.*;
import com.splicemachine.test.Transactions;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * ITs relating to transactional behavior of UPDATE calls.
 *
 * @author Scott Fines
 *         Date: 11/10/14
 */
@Category({Transactions.class})
public class UpdateTransactionIT {
    private static final SpliceSchemaWatcher schemaWatcher = new SpliceSchemaWatcher(UpdateTransactionIT.class.getSimpleName());

    public static final SpliceTableWatcher table = new SpliceTableWatcher("A",schemaWatcher.schemaName,"(a int, b int)");

    public static final SpliceWatcher classWatcher = new SpliceWatcher();

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(classWatcher)
            .around(schemaWatcher)
            .around(table).around(new SpliceDataWatcher() {
                @Override
                protected void starting(Description description) {
                    try {
                        Statement statement = classWatcher.getStatement();
                        statement.execute("insert into "+table+" (a,b) values (1,1)");
                    }catch(Exception e){
                        throw new RuntimeException(e);
                    }
                }
            });

    private static TestConnection conn1;
    private static TestConnection conn2;

    private long conn1Txn;
    private long conn2Txn;

    @BeforeClass
    public static void setUpClass() throws Exception {
        conn1 = classWatcher.getOrCreateConnection();
        conn2 = classWatcher.createConnection();
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        conn1.close();
        conn2.close();
    }

    @After
    public void tearDown() throws Exception {
        conn1.rollback();
        conn1.reset();
        conn2.rollback();
        conn2.reset();
    }

    @Before
    public void setUp() throws Exception {
        conn1.setAutoCommit(false);
        conn2.setAutoCommit(false);
        conn1Txn = conn1.getCurrentTransactionId();
        conn2Txn = conn2.getCurrentTransactionId();
    }

    @Test
    public void testUpdateAndSelectPreparedStatementsWorkWithCommit() throws Exception {
        /*Regression test for DB-2199*/
        PreparedStatement select = conn1.prepareStatement("select a,b from "+table+" where b = ?");
        conn1.commit();
        PreparedStatement update = conn1.prepareStatement("update "+ table+" set a = a+1 where b = ?");
        conn1.commit();

        //now make sure that the order is correct
        select.setInt(1,1);
        ResultSet selectRs = select.executeQuery();
        Assert.assertTrue("Did not find any rows!",selectRs.next());
        Assert.assertEquals("Incorrect value for a!",1,selectRs.getInt(1));

        //issue the update
        update.setInt(1,1);
        update.execute();

        //make sure the value changed within this transaction
        select.setInt(1,1);
        selectRs = select.executeQuery();
        Assert.assertTrue("Did not find any rows!",selectRs.next());
        Assert.assertEquals("Incorrect value for a!",2,selectRs.getInt(1));

        //now commit, and make sure that the change has been propagated
        conn1.commit();

        select.setInt(1,1);
        selectRs = select.executeQuery();
        Assert.assertTrue("Did not find any rows!",selectRs.next());
        Assert.assertEquals("Incorrect value for a!",2,selectRs.getInt(1));
    }
}
