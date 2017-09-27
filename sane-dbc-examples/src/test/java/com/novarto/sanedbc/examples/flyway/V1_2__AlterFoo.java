package com.novarto.sanedbc.examples.flyway;

import com.novarto.sanedbc.core.ops.UpdateOp;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;

/**
 * Simple migration that inserts a new row in FOO table
 *
 * @see JdbcMigration
 */
@SuppressWarnings("checkstyle:TypeName")
public class V1_2__AlterFoo implements JdbcMigration
{
    @Override
    public void migrate(Connection c) throws Exception
    {
        new UpdateOp("INSERT INTO FLYWAY.FOO (DESCRIPTION) VALUES (?)", ps -> ps.setString(1, "description 3")).run(c);
    }
}
