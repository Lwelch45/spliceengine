package com.splicemachine.derby.impl.stats;

import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceTableWatcher;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.derby.test.framework.TestConnection;
import com.splicemachine.test.SerialTest;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.sql.*;

/**
 * Specific ITs for statistics tests.
 *
 * @author Scott Fines
 *         Date: 6/24/15
 */
public class FixedStatsIT{
    private static final SpliceWatcher classWatcher = new SpliceWatcher();
    private static final SpliceSchemaWatcher schema = new SpliceSchemaWatcher(FixedStatsIT.class.getSimpleName().toUpperCase());

    private static final SpliceTableWatcher charDelete = new SpliceTableWatcher("CHAR_DELETE",schema.schemaName,"(c char(10))");

    @ClassRule
    public static final TestRule rule = RuleChain.outerRule(classWatcher)
            .around(schema)
            .around(charDelete);

    private static TestConnection conn;

    @BeforeClass
    public static void setUpClass() throws Exception {
        conn = classWatcher.getOrCreateConnection();
        conn.setAutoCommit(false);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        conn.reset();
    }

    @After
    public void afterMethod() throws Exception{
        conn.rollback();
    }

    @Test
    public void testCorrectRowCountsAfterDelete() throws Exception{
        /*
         * Regression test for DB-3468
         */
        try(PreparedStatement ps=conn.prepareStatement("insert into "+charDelete+" (c) values (?)")){
            ps.setString(1,"1");
            ps.execute();
            ps.setString(1,"2");
            ps.execute();
        }

        conn.collectStats(schema.schemaName,charDelete.tableName);
        try(Statement s = conn.createStatement()){
            assertExpectedCount(s,2);

            int changed = s.executeUpdate("delete from "+charDelete);
            Assert.assertEquals("did not properly delete values!",2,changed);

            conn.collectStats(schema.schemaName,charDelete.tableName);
            assertExpectedCount(s,0);
        }
    }

    private void assertExpectedCount(Statement s,int expectedCount) throws SQLException{
        try(ResultSet resultSet=s.executeQuery("select * from sys.systablestatistics "+
                "where schemaname = '"+schema.schemaName+"' and tablename = '"+charDelete.tableName+"'")){
            Assert.assertTrue("No row returned after stats collection!",resultSet.next());
            long rowCount=resultSet.getLong("TOTAL_ROW_COUNT");
            /*
             * WARNING(-sf-): If you add more data to the charDelete table, you might contaminate this number, so
             * be careful!
             */
            Assert.assertEquals("Incorrect row count!",expectedCount,rowCount);
        }
    }
}