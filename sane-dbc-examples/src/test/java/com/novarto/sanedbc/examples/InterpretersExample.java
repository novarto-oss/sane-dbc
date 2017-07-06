package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.AggregateOp;
import com.novarto.sanedbc.core.ops.EffectOp;
import com.novarto.sanedbc.hikari.Hikari;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Before;
import org.junit.Test;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.lift;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class InterpretersExample
{

    @Before
    public void setup() throws SQLException
    {

        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")
        );

        dbi.submit(new EffectOp("CREATE TABLE DUMMY (ID INTEGER PRIMARY KEY, X NVARCHAR(200))"));

    }

    @Test
    public void testIt()
    {
        // a datasource backed by a HikariCP connection pool
        HikariDataSource hikariDS = Hikari.createHikari("jdbc:hsqldb:mem:test", "sa", "", new Properties());


        SyncDbInterpreter sync = new SyncDbInterpreter(
                //transform a datasource to a Try0<Connection, SqlException>
                lift(hikariDS)
        );

        sync.submit(new EffectOp("INSERT INTO DUMMY VALUES(1,'a')"));
        Long count = sync.submit(new AggregateOp("SELECT COUNT(*) FROM DUMMY"));
        assertThat(count, is(1L));


        try
        {
            sync.transact(new EffectOp(
                    "INSERT INTO DUMMY VALUES((1,'a'), (2, 'b'))"
            ));
            fail("expected constraint violation");
        }
        catch (RuntimeException e)
        {
            assertThat(e.getCause(), is(instanceOf(SQLException.class)));
            count = sync.submit(new AggregateOp("SELECT COUNT(*) FROM DUMMY"));
            //transactional, no records were updated
            assertThat(count, is(1L));

        }


    }
}
