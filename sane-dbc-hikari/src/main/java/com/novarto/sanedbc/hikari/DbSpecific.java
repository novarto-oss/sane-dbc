package com.novarto.sanedbc.hikari;

import java.util.Properties;

public class DbSpecific
{
    public static Properties defaultMysqlConnectionProps(boolean profile)
    {
        Properties config = new Properties();
        config.setProperty("cachePrepStmts", "true");
        config.setProperty("prepStmtCacheSize", "250");
        config.setProperty("prepStmtCacheSqlLimit", "2048");
        config.setProperty("rewriteBatchedStatements", "true");
        config.setProperty("allowMultiQueries", "true");
        config.setProperty("characterEncoding", "utf-8");
        config.setProperty("connectionCollation", "utf8_unicode_ci");

        if (profile)
        {
            config.setProperty("profileSQL", "true");

        }

        return config;
    }
}
