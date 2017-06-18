package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.BatchUpdateOp;
import com.novarto.sanedbc.core.ops.SelectOp;
import com.novarto.sanedbc.core.ops.UpdateOp;
import fj.Unit;
import fj.control.db.DB;
import fj.data.List;
import fj.data.Option;
import org.junit.After;
import org.junit.Test;

import java.sql.DriverManager;

import static com.novarto.lang.CanBuildFrom.fjListCanBuildFrom;
import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;
import static com.novarto.sanedbc.core.ops.DbOps.sequence;
import static fj.data.Option.some;
import static java.text.MessageFormat.format;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class DescribeVsInterpret
{
    @Test
    public void nothingHappens()
    {
        @SuppressWarnings("unused")
        SelectOp.FjList<String> selectThem = new SelectOp.FjList<>(
                "SELECT FOO FROM WHATEVER",
                NO_BINDER,
                rs -> rs.getString(1)
        );
    }

    @Test
    public void syncInterpreter()
    {
        // create a synchronous DB interpreter. It is a stateless object, and the act of creating one is also
        // referentially transparent
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                // provide a piece of code which knows how to spawn connections
                // in this case we are just using the DriverManager
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")
        );

        // submit an Update (mutate) operation which creates a table
        dbi.submit(new UpdateOp(
                "CREATE TABLE FOO (WHATEVER VARCHAR(200))",
                NO_BINDER
        ));

        // objects we will insert in the table
        List<String> helloSaneDbc = List.arrayList("hello", "sane", "dbc");


        Option<Integer> updateCount = dbi.submit(
                // an operation which inserts an iterable of objects in a table via addBatch / executeBatch
                new BatchUpdateOp<>(
                        "INSERT INTO FOO(WHATEVER) VALUES(?)",
                        x -> preparedStatement -> preparedStatement.setString(1, x),
                        helloSaneDbc
                )
        );

        // the operation returns an optional update count, since the JDBC driver might not return an update count at all
        assertThat(updateCount, is(some(3)));


        List<String> result = dbi.submit(
                // select all of the objects in the table
                new SelectOp.FjList<>("SELECT WHATEVER FROM FOO", NO_BINDER, resultSet -> resultSet.getString(1))
        );


        assertThat(result, is(helloSaneDbc));
    }

    @After
    public void cleanup()
    {
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:DescribeVsInterpret", "sa", "")
        );


        DB<List<String>> selectAllTables = new SelectOp.FjList<>(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='DescribeVsInterpret'",
                NO_BINDER,
                rs -> rs.getString(1)
        );

        DB<Unit> dropAllTables = selectAllTables
                .bind(tables ->
                        sequence(tables.map(tableName ->
                                new UpdateOp(format("DROP TABLE {0}", tableName), NO_BINDER)), fjListCanBuildFrom()))
                .map(ignore -> Unit.unit());

        dbi.submit(dropAllTables);

    }
}
