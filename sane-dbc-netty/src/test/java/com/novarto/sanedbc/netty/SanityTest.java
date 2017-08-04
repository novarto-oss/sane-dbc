package com.novarto.sanedbc.netty;

import com.novarto.sanedbc.hikari.Hikari;
import com.zaxxer.hikari.HikariDataSource;
import fj.control.db.DB;
import fj.function.Try1;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.UnorderedThreadPoolEventExecutor;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static com.novarto.lang.testutil.TestUtil.tryTo;
import static com.novarto.sanedbc.hikari.Hikari.gracefulShutdown;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class SanityTest
{
    private static FutureInterpreter dbAsync;
    private static HikariDataSource ds;
    private static UnorderedThreadPoolEventExecutor ex;

    @BeforeClass public static void setupHikari()
    {
        ds = Hikari.createHikari("jdbc:hsqldb:mem:JdbcUtilsTest", "sa", "", new Properties());


        ex = Hikari.createExecutorFor(ds, false, () ->
                new UnorderedThreadPoolEventExecutor(0, new DefaultThreadFactory("sanity-test"))
        );

        dbAsync = new FutureInterpreter(ds, SanityTest.ex);
    }

    @Test public void sanity()
    {
        Future<Integer> success = dbAsync.submit(DB.unit(42));

        SQLException ex = new SQLException("failed i have");
        Future<Integer> fail = dbAsync.submit(DB.db((Try1<Connection, Integer, SQLException>) c ->
        {
            assertThat(c, is(notNullValue()));
            throw ex;
        }));

        assertThat(awaitAndGet(success), is(42));

        assertThat(awaitAndGetFailure(fail), is(ex));

    }

    private static <A> A awaitAndGet(Future<A> success)
    {
        return tryTo(() -> success.get());
    }

    private static Throwable awaitAndGetFailure(Future<?> failure)
    {
        Throwable result = tryTo(() -> failure.await()).cause();
        if(result == null)
        {
            throw new IllegalStateException("expected throwable, got: " + failure.getNow());
        }

        return result;

    }

    @AfterClass public static void shutdownHikari()
    {
        gracefulShutdown(ex, ds);
    }

}
