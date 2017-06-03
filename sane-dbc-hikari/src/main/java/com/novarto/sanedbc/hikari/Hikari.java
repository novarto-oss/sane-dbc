package com.novarto.sanedbc.hikari;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
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

    public static ListeningExecutorService createExecutorFor(HikariDataSource ds, boolean shutdownOnJvmExit)
    {
        ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        if (shutdownOnJvmExit)
        {
            final Thread datasourceShutdownHook = new Thread(() -> gracefulShutdown(executor, ds));

            datasourceShutdownHook.setName("Relational DB Shutdown Hook for jdbc url " + ds.getJdbcUrl());
            Runtime.getRuntime().addShutdownHook(datasourceShutdownHook);

        }

        return executor;

    }


    public static void gracefulShutdown(ExecutorService ex, HikariDataSource ds)
    {
        ds.close();
        MoreExecutors.shutdownAndAwaitTermination(ex, 5, TimeUnit.SECONDS);
    }

}
