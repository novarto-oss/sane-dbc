package com.novarto.sanedbc.core.interpreter;

import com.novarto.sanedbc.core.ops.DbOps;
import fj.control.db.DB;
import fj.function.Try0;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * A set of utilities to aid in implementing {@link DB} interpreters.
 */
public final class InterpreterUtils
{
    private InterpreterUtils()
    {
        throw new UnsupportedOperationException();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(DbOps.class);

    /**
     * Returns an operation that will try to rollback the given operation upon failure.
     *
     * - Upon any instance of {@link Throwable} caught, a connection rollback will be issued
     * - The original throwable instance will be preserved without being wrapped
     * - If the connection auto-commit was mutated as part of this transformation, connection setAutoCommit
     *  will be finally issued to the connection.
     */
    public static <A> DB<A> transactional(DB<A> op)
    {

        return new DB<A>()
        {
            @Override public A run(Connection c) throws SQLException
            {
                final boolean wasAutocommit = c.getAutoCommit();
                Throwable th = null;
                if (wasAutocommit)
                {
                    c.setAutoCommit(false);
                }

                try
                {
                    A result = op.run(c);
                    c.commit();
                    return result;
                }
                catch (Throwable e)
                {
                    th = e;
                    c.rollback();
                    throw e;

                }
                finally
                {
                    try
                    {
                        if (wasAutocommit)
                        {
                            c.setAutoCommit(true);
                        }
                    }
                    catch (SQLException e)
                    {
                        if (th != null)
                        {
                            th.addSuppressed(e);
                        }
                        else
                        {
                            throw e;
                        }
                    }
                }

            }
        };
    }

    /**
     * Lifts a DataSource to Try0<Connection, SQLException> (i.e. converts it to Try0<Connection, SQLException>)
     */
    public static Try0<Connection, SQLException> lift(DataSource ds)
    {
        return ds::getConnection;
    }
}
