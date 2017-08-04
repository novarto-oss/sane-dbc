package com.novarto.sanedbc.hikari;

import com.novarto.lang.ConcurrentUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fj.F0;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Hikari
{
    private static final Logger LOGGER = LoggerFactory.getLogger(Hikari.class);

    public static HikariDataSource createHikari(String url, String user, String pass, Properties dsProps)
    {
        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);

        config.setDataSourceProperties(dsProps);

        LOGGER.info("creating hikaricp datasource. using jdbc url {}", config.getJdbcUrl());

        return new HikariDataSource(config);
    }

    public static ExecutorService createExecutorFor(HikariDataSource ds, boolean shutdownOnJvmExit)
    {
        return createExecutorFor(ds, shutdownOnJvmExit, Executors::newCachedThreadPool);

    }

    public static <A extends ExecutorService> A createExecutorFor(HikariDataSource ds, boolean shutdownOnJvmExit,
            F0<A> ctor
    )
    {
        A executor = ctor.f();

        if (shutdownOnJvmExit)
        {
            String serviceName = "Shutdown hook for hikari@" + ds.getJdbcUrl();
            final Thread datasourceShutdownHook = new Thread(() -> gracefulShutdown(executor, ds));
            datasourceShutdownHook.setName(serviceName);
            Runtime.getRuntime().addShutdownHook(datasourceShutdownHook);

        }

        return executor;

    }


    public static void gracefulShutdown(ExecutorService ex, HikariDataSource ds)
    {
        ds.close();
        ConcurrentUtil.shutdownAndAwaitTermination(ex, 5, TimeUnit.SECONDS);
    }

}
