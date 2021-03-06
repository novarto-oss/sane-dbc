package com.novarto.sanedbc.guava;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.novarto.sanedbc.hikari.Hikari;
import com.zaxxer.hikari.HikariDataSource;
import fj.control.db.DB;
import fj.function.Try1;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static com.novarto.lang.guava.testutil.FuturesTestUtil.awaitAndGet;
import static com.novarto.lang.guava.testutil.FuturesTestUtil.awaitAndGetFailure;
import static com.novarto.sanedbc.hikari.Hikari.gracefulShutdown;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class SanityTest
{
    private static GuavaDbInterpreter dbAsync;
    private static HikariDataSource ds;
    private static ListeningExecutorService ex;

    @BeforeClass public static void setupHikari()
    {
        ds = Hikari.createHikari("jdbc:hsqldb:mem:JdbcUtilsTest", "sa", "", new Properties());
        ex = MoreExecutors.listeningDecorator(Hikari.createExecutorFor(ds, false));

        dbAsync = new GuavaDbInterpreter(ds, ex);
    }

    @Test public void sanity()
    {
        ListenableFuture<Integer> success = dbAsync.submit(DB.unit(42));

        SQLException ex = new SQLException("failed i have");
        ListenableFuture<Integer> fail = dbAsync.submit(DB.db((Try1<Connection, Integer, SQLException>) c ->
        {
            assertThat(c, is(notNullValue()));
            throw ex;
        }));

        assertThat(awaitAndGet(success), is(42));

        assertThat(awaitAndGetFailure(fail), is(ex));

    }

    @AfterClass public static void shutdownHikari()
    {
        gracefulShutdown(ex, ds);
    }

}
