package com.novarto.sanedbc.core.interpreter;

import com.novarto.lang.ConcurrentUtil;
import com.novarto.sanedbc.core.ops.EffectOp;
import com.novarto.sanedbc.core.ops.SelectOp;
import com.novarto.sanedbc.core.ops.UpdateOp;
import fj.Unit;
import fj.control.db.DB;
import fj.data.List;
import junit.framework.AssertionFailedError;
import org.hsqldb.jdbc.JDBCPool;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.*;

import static com.novarto.lang.testutil.TestUtil.tryTo;
import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.lift;
import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;
import static fj.data.List.arrayList;
import static fj.data.List.single;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AsyncDbInterpreterTest
{

    private static AsyncDbInterpreter dbi;
    private static SyncDbInterpreter sync;
    private static ExecutorService executor;
    private static JDBCPool ds;

    @BeforeClass public static void setupSuite()
    {
        executor = Executors.newCachedThreadPool();

        ds = new JDBCPool();
        ds.setURL("jdbc:hsqldb:mem:JdbcUtilsTest");
        ds.setUser("sa");
        ds.setPassword("");

        dbi = new AsyncDbInterpreter(ds, executor);
        sync = new SyncDbInterpreter(lift(ds));

        sync.transact(new EffectOp("CREATE TABLE BAR (BAZ VARCHAR(100))"));
    }

    @Test public void testIt()
    {

        assertThat(awaitSuccess(dbi.submit(insert("x"))), is(1));

        assertThat(awaitSuccess(dbi.submit(selectAll())), is(single("x")));

        SQLException ex = new SQLException("failed i have");
        assertThat(
                awaitFailure(dbi.transact(new DB<Unit>()
                {
                    @Override public Unit run(Connection c) throws SQLException
                    {
                        insert("a").run(c);
                        insert("b").run(c);
                        throw ex;
                    }
                })),
                is(ex)
        );

        assertThat(awaitSuccess(dbi.submit(selectAll())), is(single("x")));

        SQLException noConn = new SQLException("no connection");
        AsyncDbInterpreter noConnection = new AsyncDbInterpreter(() -> {
            throw noConn;
        }, executor);

        assertThat(awaitFailure(noConnection.transact(insert("alabala"))), is(noConn));

        assertThat(awaitSuccess(dbi.submit(selectAll())), is(single("x")));

        assertThat(
                awaitSuccess(dbi.transact(insert("y").bind(ignore -> insert("z")).map(ignore2 -> Unit.unit()))),
                is(Unit.unit())
        );

        assertThat(awaitSuccess(dbi.submit(selectAll())), is(arrayList("x", "y", "z")));

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
    public static void teardownSuite() throws SQLException
    {
        sync.transact(new EffectOp("DELETE FROM BAR"));
        ds.close(0);
        ConcurrentUtil.shutdownAndAwaitTermination(executor, 5, TimeUnit.SECONDS);

    }

    private static <A> A awaitSuccess(CompletableFuture<A> fut)
    {
        return tryTo(() -> fut.get());
    }

    private static Throwable awaitFailure(CompletableFuture<?> fut)
    {
       try
       {
           Object result = fut.get();
           throw new AssertionFailedError("unexpected success: " + result);
       }
       catch (InterruptedException e)
       {
           throw new RuntimeException(e);
       }
       catch (ExecutionException e)
       {
           return e.getCause();
       }
    }
}
