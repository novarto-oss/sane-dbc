package com.novarto.sanedbc.core.interpreter;

import com.novarto.sanedbc.core.ops.EffectOp;
import com.novarto.sanedbc.core.ops.SelectOp;
import com.novarto.sanedbc.core.ops.UpdateOp;
import fj.Unit;
import fj.control.db.DB;
import fj.data.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;
import static fj.data.List.arrayList;
import static fj.data.List.single;
import static fj.data.Validation.fail;
import static fj.data.Validation.success;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ValidationDbInterpreterTest
{
    private static final ValidationDbInterpreter DB = new ValidationDbInterpreter(
            () -> DriverManager.getConnection("jdbc:hsqldb:mem:JdbcUtilsTest", "sa", ""));

    @BeforeClass public static void setupSuite()
    {
        DB.transact(new EffectOp("CREATE TABLE BAR (BAZ VARCHAR(100))"));
    }

    @Test public void testIt()
    {
        assertThat(DB.submit(insert("x")), is(success(1)));

        assertThat(DB.submit(selectAll()), is(success(single("x"))));

        SQLException ex = new SQLException("failed i have");
        assertThat(
                DB.transact(new DB<Unit>()
                {
                    @Override public Unit run(Connection c) throws SQLException
                    {
                        insert("a").run(c);
                        insert("b").run(c);
                        throw ex;
                    }
                }),
                is(fail(ex))
        );

        assertThat(DB.submit(selectAll()), is(success(single("x"))));

        SQLException noConn = new SQLException("no connection");
        ValidationDbInterpreter noConnection = new ValidationDbInterpreter(() -> {
            throw noConn;
        });

        assertThat(noConnection.transact(insert("alabala")), is(fail(noConn)));

        assertThat(DB.submit(selectAll()), is(success(single("x"))));

        assertThat(
                DB.transact(insert("y").bind(ignore -> insert("z")).map(ignore2 -> Unit.unit())),
                is(success(Unit.unit()))
        );

        assertThat(DB.submit(selectAll()), is(success(arrayList("x", "y", "z"))));

    }

    private DB<Integer> insert(String x)
    {
        return new UpdateOp("INSERT INTO BAR VALUES(?)", ps -> ps.setString(1, x));
    }

    private DB<List<String>> selectAll()
    {
        return new SelectOp.FjList<>("SELECT * FROM BAR", NO_BINDER, rs -> rs.getString(1));
    }

    @AfterClass
    public static void teardownSuite()
    {
        DB.transact(new EffectOp("DROP TABLE BAR"));
    }
}
