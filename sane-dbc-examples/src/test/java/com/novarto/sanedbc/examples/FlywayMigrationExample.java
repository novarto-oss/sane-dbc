package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.AggregateOp;
import org.flywaydb.core.Flyway;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * Database migrations are a great way to control the state of an application in the database. This is an example of usage of
 * {@link Flyway} along with sane-dbc.
 * <p>
 * This example uses two migrations:
 * <ul>
 *     <li>Static sql file V1_1_DBInit.sql which is placed in resources dir
 *     <li>JDBC Java Migration {@link com.novarto.sanedbc.examples.flyway.V1_2__AlterFoo}
 * </ul>
 *
 * @see <a href="https://flywaydb.org/">https://flywaydb.org/</a>
 */
public class FlywayMigrationExample
{

    private static final Flyway FLYWAY = new Flyway();
    private static final String FLYWAY_JDBC_URL = "jdbc:hsqldb:mem:flyway";

    @BeforeClass
    public static void setup()
    {
        // set schema name
        FLYWAY.setSchemas("FLYWAY");

        // set datasource
        FLYWAY.setDataSource(FLYWAY_JDBC_URL, "sa", "");


        // set location folders which contain migrations that needs to be applied by flyway
        final String packageName = FlywayMigrationExample.class.getPackage().getName();
        final String packageFolder = packageName.replace(".", "/");
        final String migrationFolder = packageFolder + "/flyway";
        final List<String> migrationFolders = new ArrayList<>();
        migrationFolders.add(migrationFolder);
        migrationFolders.add("flyway");
        FLYWAY.setLocations(migrationFolders.toArray(new String[migrationFolders.size()]));

        // run migrations
        FLYWAY.migrate();
    }

    @Test
    public void checkMigrationsAreApplied()
    {
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                // provide a piece of code which knows how to spawn connections
                // in this case we are just using the DriverManager
                () -> DriverManager.getConnection(FLYWAY_JDBC_URL, "sa", "")
        );

        // check that after flyway migrations are applied the count of FOO is 3
        final Long count = dbi.submit(new AggregateOp("SELECT COUNT(*) FROM FLYWAY.FOO"));
        assertThat(count, is(3L));
    }

    @AfterClass
    public static void cleanup()
    {
        FLYWAY.clean();
    }
}
