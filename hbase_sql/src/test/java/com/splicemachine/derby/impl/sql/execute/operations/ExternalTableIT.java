package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.derby.test.framework.SpliceSchemaWatcher;
import com.splicemachine.derby.test.framework.SpliceUnitTest;
import com.splicemachine.derby.test.framework.SpliceWatcher;
import com.splicemachine.homeless.TestUtils;
import com.splicemachine.test_dao.TriggerBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;


import java.io.File;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 * IT's for external table functionality
 *
 */
public class ExternalTableIT extends SpliceUnitTest{

    private static final String SCHEMA_NAME = ExternalTableIT.class.getSimpleName().toUpperCase();
    private static final SpliceWatcher spliceClassWatcher = new SpliceWatcher(SCHEMA_NAME);
    private static final SpliceSchemaWatcher spliceSchemaWatcher = new SpliceSchemaWatcher(SCHEMA_NAME);

    private TriggerBuilder tb = new TriggerBuilder();
    @Rule
    public SpliceWatcher methodWatcher = new SpliceWatcher(SCHEMA_NAME);

    @ClassRule
    public static TestRule chain = RuleChain.outerRule(spliceClassWatcher)
            .around(spliceSchemaWatcher);

    @BeforeClass
    public static void cleanoutDirectory() {
        try {
            File file = new File(getExternalResourceDirectory());
            if (file.exists())
                FileUtils.deleteDirectory(new File(getExternalResourceDirectory()));
            file.mkdir();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    public void testInvalidSyntaxParquet() throws Exception {
        try {
            // Row Format not supported for Parquet
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 int) partitioned by (col1) " +
                    "row format delimited fields terminated by ',' escaped by '\\' " +
                    "lines terminated by '\\n' STORED AS PARQUET LOCATION '/foobar/foobar'");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT01",e.getSQLState());
        }
    }

    @Test
    public void testInvalidSyntaxORC() throws Exception {
        try {
            // Row Format not supported for Parquet
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 int) partitioned by (col1) " +
                    "row format delimited fields terminated by ',' escaped by '\\' " +
                    "lines terminated by '\\n' STORED AS ORC LOCATION '/foobar/foobar'");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT02",e.getSQLState());
        }
    }


    @Test
    public void testStoredAsRequired() throws Exception {
        try {
            // Location Required For Parquet
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 int) LOCATION 'foobar'");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT03",e.getSQLState());
        }
    }

    @Test
    public void testLocationRequired() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 int) STORED AS PARQUET");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT04",e.getSQLState());
        }
    }


    @Test
    public void testNoPrimaryKeysOnExternalTables() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 int, primary key (col1)) STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT06",e.getSQLState());
        }
    }

    @Test
    public void testNoCheckConstraintsOnExternalTables() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 int, SALARY DECIMAL(9,2) CONSTRAINT SAL_CK CHECK (SALARY >= 10000)) STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT07",e.getSQLState());
        }
    }

    @Test
    public void testNoReferenceConstraintsOnExternalTables() throws Exception {
        try {
            methodWatcher.executeUpdate("create table Cities (col1 int, col2 int, primary key (col1))");
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 int, CITY_ID INT CONSTRAINT city_foreign_key\n" +
                    " REFERENCES Cities) STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT08",e.getSQLState());
        }
    }

        @Test
        public void testNoUniqueConstraintsOnExternalTables() throws Exception {
            try {
                methodWatcher.executeUpdate("create external table foo (col1 int, col2 int unique)" +
                        " STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
                Assert.fail("Exception not thrown");
            } catch (SQLException e) {
                Assert.assertEquals("Wrong Exception","EXT09",e.getSQLState());
            }
        }

    @Test
    public void testNoGenerationClausesOnExternalTables() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table foo (col1 int, col2 varchar(24), col3 GENERATED ALWAYS AS ( UPPER(col2) ))" +
                    " STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT10",e.getSQLState());
        }
    }

    @Test
    public void testCannotUpdateExternalTable() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table update_foo (col1 int, col2 varchar(24))" +
                    " STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            methodWatcher.executeUpdate("update update_foo set col1 = 4");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT05",e.getSQLState());
        }
    }

    @Test
    public void testCannotDeleteExternalTable() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table delete_foo (col1 int, col2 varchar(24))" +
                    " STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            methodWatcher.executeUpdate("delete from delete_foo where col1 = 4");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT05",e.getSQLState());
        }
    }


    @Test
    public void testFileExistingNotDeleted() throws  Exception{
        String tablePath = getExternalResourceDirectory()+"location_existing_file";

        File newFile = new File(tablePath);
        newFile.createNewFile();
        long lastModified = newFile.lastModified();


        methodWatcher.executeUpdate(String.format("create external table table_to_existing_file (col1 int, col2 varchar(24))" +
                " STORED AS PARQUET LOCATION '%s'",tablePath));
        Assert.assertEquals(String.format("File : %s have been modified and it shouldn't",tablePath),lastModified,newFile.lastModified());
    }

    @Test
    public void refreshRequireExternalTable() throws  Exception{
        String tablePath = getExternalResourceDirectory()+"external_table_refresh";

        methodWatcher.executeUpdate(String.format("create external table external_table_refresh (col1 int, col2 varchar(24))" +
                " STORED AS PARQUET LOCATION '%s'",tablePath));

        PreparedStatement ps = spliceClassWatcher.prepareCall("CALL  SYSCS_UTIL.SYSCS_REFRESH_EXTERNAL_TABLE(?,?) ");
        ps.setString(1, "EXTERNALTABLEIT");
        ps.setString(2, "EXTERNAL_TABLE_REFRESH");
        ResultSet rs = ps.executeQuery();
        rs.next();

        Assert.assertEquals("Error with refresh external table","EXTERNALTABLEIT.EXTERNAL_TABLE_REFRESH schema table refreshed",  rs.getString(1));


    }



    @Test
    public void testWriteReadFromSimpleParquetExternalTable() throws Exception {
         String tablePath = getExternalResourceDirectory()+"simple_parquet";
            methodWatcher.executeUpdate(String.format("create external table simple_parquet (col1 int, col2 varchar(24))" +
                    " STORED AS PARQUET LOCATION '%s'",tablePath));
            int insertCount = methodWatcher.executeUpdate(String.format("insert into simple_parquet values (1,'XXXX')," +
                    "(2,'YYYY')," +
                    "(3,'ZZZZ')"));
            Assert.assertEquals("insertCount is wrong",3,insertCount);
            ResultSet rs = methodWatcher.executeQuery("select * from simple_parquet");
            Assert.assertEquals("COL1 |COL2 |\n" +
                    "------------\n" +
                    "  1  |XXXX |\n" +
                    "  2  |YYYY |\n" +
                    "  3  |ZZZZ |",TestUtils.FormattedResult.ResultFactory.toString(rs));
        ResultSet rs2 = methodWatcher.executeQuery("select distinct col1 from simple_parquet");
        Assert.assertEquals("COL1 |\n" +
                "------\n" +
                "  1  |\n" +
                "  2  |\n" +
                "  3  |",TestUtils.FormattedResult.ResultFactory.toString(rs2));

        //Make sure empty file is created
        Assert.assertTrue(String.format("Table %s hasn't been created",tablePath), new File(tablePath).exists());

    }



    @Test
    public void testWriteReadFromPartitionedParquetExternalTable() throws Exception {
        String tablePath =  getExternalResourceDirectory()+"partitioned_parquet";
                methodWatcher.executeUpdate(String.format("create external table partitioned_parquet (col1 int, col2 varchar(24))" +
                "partitioned by (col2) STORED AS PARQUET LOCATION '%s'", getExternalResourceDirectory()+"partitioned_parquet"));
        int insertCount = methodWatcher.executeUpdate(String.format("insert into partitioned_parquet values (1,'XXXX')," +
                "(2,'YYYY')," +
                "(3,'ZZZZ')"));
        Assert.assertEquals("insertCount is wrong",3,insertCount);
        ResultSet rs = methodWatcher.executeQuery("select * from partitioned_parquet");
        Assert.assertEquals("COL1 |COL2 |\n" +
                "------------\n" +
                "  1  |XXXX |\n" +
                "  2  |YYYY |\n" +
                "  3  |ZZZZ |",TestUtils.FormattedResult.ResultFactory.toString(rs));

        //Make sure empty file is created
        Assert.assertTrue(String.format("Table %s hasn't been created",tablePath), new File(tablePath).exists());

    }

    @Test @Ignore
    public void testWriteReadFromSimpleORCExternalTable() throws Exception {
        methodWatcher.executeUpdate(String.format("create external table simple_orc (col1 int, col2 varchar(24))" +
                " STORED AS ORC LOCATION '%s'", getExternalResourceDirectory()+"simple_orc"));
        int insertCount = methodWatcher.executeUpdate(String.format("insert into simple_orc values (1,'XXXX')," +
                "(2,'YYYY')," +
                "(3,'ZZZZ')"));
        Assert.assertEquals("insertCount is wrong",3,insertCount);
        ResultSet rs = methodWatcher.executeQuery("select * from simple_orc");
        Assert.assertEquals("COL1 |COL2 |\n" +
                "------------\n" +
                "  1  |XXXX |\n" +
                "  2  |YYYY |\n" +
                "  3  |ZZZZ |",TestUtils.FormattedResult.ResultFactory.toString(rs));
    }

    @Test @Ignore
    public void testWriteReadFromPartitionedORCExternalTable() throws Exception {
        methodWatcher.executeUpdate(String.format("create external table partitioned_orc (col1 int, col2 varchar(24))" +
                "partitioned by (col2) STORED AS ORC LOCATION '%s'", getExternalResourceDirectory()+"partitioned_orc"));
        int insertCount = methodWatcher.executeUpdate(String.format("insert into partitioned_orc values (1,'XXXX')," +
                "(2,'YYYY')," +
                "(3,'ZZZZ')"));
        Assert.assertEquals("insertCount is wrong",3,insertCount);
        ResultSet rs = methodWatcher.executeQuery("select * from partitioned_orc");
        Assert.assertEquals("COL1 |COL2 |\n" +
                "------------\n" +
                "  1  |XXXX |\n" +
                "  2  |YYYY |\n" +
                "  3  |ZZZZ |",TestUtils.FormattedResult.ResultFactory.toString(rs));
    }

    @Test
    public void testWriteReadFromSimpleTextExternalTable() throws Exception {
        methodWatcher.executeUpdate(String.format("create external table simple_text (col1 int, col2 varchar(24), col3 boolean)" +
                " STORED AS TEXTFILE LOCATION '%s'", getExternalResourceDirectory()+"simple_text"));
        int insertCount = methodWatcher.executeUpdate(String.format("insert into simple_text values (1,'XXXX',true)," +
                "(2,'YYYY',false)," +
                "(3,'ZZZZ', true)"));
        Assert.assertEquals("insertCount is wrong",3,insertCount);
        ResultSet rs = methodWatcher.executeQuery("select * from simple_text");
        Assert.assertEquals("COL1 |COL2 |COL3  |\n" +
                "-------------------\n" +
                "  1  |XXXX |true  |\n" +
                "  2  |YYYY |false |\n" +
                "  3  |ZZZZ |true  |",TestUtils.FormattedResult.ResultFactory.toString(rs));
    }

    @Test
    public void validateReadParquetFromEmptyDirectory() throws Exception {
        File directory = new File(String.valueOf(getExternalResourceDirectory()+"parquet_empty"));
        if (!directory.exists())
            directory.mkdir();
        methodWatcher.executeUpdate(String.format("create external table parquet_empty (col1 int, col2 varchar(24))" +
                " STORED AS PARQUET LOCATION '%s'", getExternalResourceDirectory()+"parquet_empty"));
        ResultSet rs = methodWatcher.executeQuery("select * from parquet_empty");
        Assert.assertEquals("",TestUtils.FormattedResult.ResultFactory.toString(rs));
    }

    @Test
    public void validateReadORCFromEmptyDirectory() throws Exception {
        File directory = new File(String.valueOf(getExternalResourceDirectory()+"orc_empty"));
        if (!directory.exists())
            directory.mkdir();
        methodWatcher.executeUpdate(String.format("create external table orc_empty (col1 int, col2 varchar(24))" +
                " STORED AS ORC LOCATION '%s'", getExternalResourceDirectory()+"orc_empty"));
        ResultSet rs = methodWatcher.executeQuery("select * from orc_empty");
        Assert.assertEquals("",TestUtils.FormattedResult.ResultFactory.toString(rs));
    }


    @Test
    public void testCannotAlterExternalTable() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table alter_foo (col1 int, col2 varchar(24))" +
                    " STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            methodWatcher.executeUpdate("alter table alter_foo add column col3 int");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT12",e.getSQLState());
        }
    }

    @Test
    public void testCannotAddIndexToExternalTable() throws Exception {
        try {
            methodWatcher.executeUpdate("create external table add_index_foo (col1 int, col2 varchar(24))" +
                    " STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");
            methodWatcher.executeUpdate("create index add_index_foo_ix on add_index_foo (col2)");
            Assert.fail("Exception not thrown");
        } catch (SQLException e) {
            Assert.assertEquals("Wrong Exception","EXT13",e.getSQLState());
        }
    }

    @Test
    public void testCannotAddTriggerToExternalTable() throws Exception {
        methodWatcher.executeUpdate("create external table add_trigger_foo (col1 int, col2 varchar(24))" +
                " STORED AS PARQUET LOCATION 'HUMPTY_DUMPTY_MOLITOR'");

        verifyTriggerCreateFails(tb.on("add_trigger_foo").named("trig").before().delete().row().then("select * from sys.systables"),
                "Cannot add triggers to external table 'ADD_TRIGGER_FOO'.");
    }



    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -
    //
    // - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

    private void createTrigger(TriggerBuilder tb) throws Exception {
        methodWatcher.executeUpdate(tb.build());
    }

    private void verifyTriggerCreateFails(TriggerBuilder tb, String expectedError) throws Exception {
        try {
            createTrigger(tb);
            fail("expected trigger creation to fail for=" + tb.build());
        } catch (Exception e) {
            assertEquals(expectedError, e.getMessage());
        }

    }

    public static String getExternalResourceDirectory() {
        return getHBaseDirectory()+"/target/external/";
    }


    @Test
    @Ignore
    public void testWriteToWrongPartitionedParquetExternalTable() throws Exception {
        try {
            methodWatcher.executeUpdate(String.format("create external table w_partitioned_parquet (col1 int, col2 varchar(24))" +
                    "partitioned by (col1) STORED AS PARQUET LOCATION '%s'", getExternalResourceDirectory() + "w_partitioned_parquet"));
            methodWatcher.executeUpdate(String.format("insert into w_partitioned_parquet values (1,'XXXX')," +
                    "(2,'YYYY')," +
                    "(3,'ZZZZ')"));
            methodWatcher.executeUpdate(String.format("create external table w_partitioned_parquet_2 (col1 int, col2 varchar(24))" +
                    "partitioned by (col2) STORED AS PARQUET LOCATION '%s'", getExternalResourceDirectory() + "w_partitioned_parquet"));
            methodWatcher.executeUpdate(String.format("insert into w_partitioned_parquet_2 values (1,'XXXX')," +
                    "(2,'YYYY')," +
                    "(3,'ZZZZ')"));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}