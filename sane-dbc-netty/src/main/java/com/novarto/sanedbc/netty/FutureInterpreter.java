package com.novarto.sanedbc.netty;

import fj.control.db.DB;
import fj.function.Try0;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.Future;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.lift;
import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.transactional;

public class FutureInterpreter
{
    private final Try0<Connection, SQLException> ds;
    private final EventExecutorGroup ex;

    public FutureInterpreter(Try0<Connection, SQLException> ds, EventExecutorGroup ex)
    {
        this.ds = ds;
        this.ex = ex;
    }

    public FutureInterpreter(DataSource ds, EventExecutorGroup ex)
    {
        this(lift(ds), ex);
    }

    /**
     * Submits this operation for execution in the executor service. The operation is executed with connection autoCommit = true,
     * i.e. non-transactionally.
     */
    public <A> Future<A> submit(DB<A> op)
    {
        return withConnection(op, true);
    }

    /**
     * Submits this operation for execution in the executor service. The operation is executed as a transaction.
     */
    public <A> Future<A> transact(DB<A> op)
    {
        return withConnection(transactional(op), false);
    }

    private <A> Future<A> withConnection(DB<A> op, boolean autoCommit)
    {
        return ex.submit(() ->
        {

            try (Connection c = getConnection(autoCommit))
            {
                return op.run(c);
            }
        });
    }

    private Connection getConnection(boolean autoCommit) throws SQLException
    {
        Connection result = ds.f();
        result.setAutoCommit(autoCommit);
        return result;
    }
}
