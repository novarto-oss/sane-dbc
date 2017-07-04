package com.novarto.sanedbc.core.interpreter;

import fj.control.db.DB;
import fj.function.Try0;

import java.sql.Connection;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.transactional;

/**
 * An interpreter for DB operations which blocks the caller thread. In addition it rethrows any SQL Exceptions as runtime ones.
 * Mostly useful for testing purposes.
 *
 */
public class SyncDbInterpreter
{
    private final Try0<Connection, SQLException> ds;

    /**
     * Construct an interpreter, given a piece of code which knows how to spawn connections, e.g. a Data Source, Connection Pool,
     * Driver Manager, etc.
     * @param ds - the data source, which can spawn connections
     */
    public SyncDbInterpreter(Try0<Connection, SQLException> ds)
    {
        this.ds = ds;
    }

    /**
     * Attempt to run this operation and return its result
     * @param doOp the operation to run
     * @param <A> the result type of the operation
     * @return the successful result
     * @throws RuntimeException if an {@link SQLException} is encountered
     */
    public <A> A submit(DB<A> doOp)
    {
        try (Connection c = ds.f())
        {
            return doOp.run(c);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }

    }

    /**
     * Attempt to run this operation and return its result. The operation is run transactionally - i.e. if any error is
     * encountered, the operation is rolled back.
     * @param doOp the operation to run
     * @param <A> the result type of the operation
     * @return the successful result
     * @throws RuntimeException if an {@link SQLException} is encountered
     */
    public <A> A transact(DB<A> doOp)
    {

        return submit(transactional(doOp));

    }
}
