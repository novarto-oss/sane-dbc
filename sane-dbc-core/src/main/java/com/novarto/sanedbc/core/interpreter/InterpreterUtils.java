package com.novarto.sanedbc.core.interpreter;

import com.novarto.sanedbc.core.ops.DbOps;
import fj.control.db.DB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * - The original throwable instance will be preserved without being mutated
     * - If the connection auto-commit was mutated as part of this transformation, connection setAutoCommit
     *  will be finally issued to the connection.
     */
    public static <A> DB<A> transactional(DB<A> op)
    {

        return new DB<A>()
        {
            @Override public A run(Connection c) throws SQLException
            {
                boolean wasAutocommit = c.getAutoCommit();
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
                catch (RuntimeException | SQLException e)
                {
                    c.rollback();
                    throw e;

                }
                catch (Error e)
                {
                    try
                    {
                        c.rollback();
                    }
                    catch (SQLException sqlE)
                    {
                        LOGGER.error("Could not roll back transaction which failed with java.lang.Error", sqlE);
                    }
                    throw e;
                }
                catch (Throwable t)
                {
                    c.rollback();
                    throw t;
                }
                finally
                {
                    if (wasAutocommit)
                    {
                        c.setAutoCommit(true);
                    }
                }

            }
        };
    }
}
