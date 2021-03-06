package com.novarto.sanedbc.guava;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import fj.control.db.DB;
import fj.function.Try0;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.lift;
import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.transactional;

/**
 * A {@link DB} interpreter that utilizes a data source to spawn connections,
 * and submits {@link DB} instances for execution in a Guava ListeningExecutorService.
 * The ListenableFuture returned will fail iff the underlying DB throws.
 *
 * Spawning of a connection happens inside an executor service thread, the submitted operation is executed in the same thread,
 * and then the connection is closed. Therefore, a connection obtained from the pool is accessed by only a single thread
 * before being returned to the pool.
 *
 */
public class GuavaDbInterpreter
{
    private final Try0<Connection, SQLException> ds;
    private final ListeningExecutorService ex;

    public GuavaDbInterpreter(Try0<Connection, SQLException> ds, ListeningExecutorService ex)
    {
        this.ds = ds;
        this.ex = ex;
    }

    public GuavaDbInterpreter(DataSource ds, ListeningExecutorService ex)
    {
        this(lift(ds), ex);
    }

    /**
     * Submits this operation for execution in the executor service. The operation is executed with connection autoCommit = true,
     * i.e. non-transactionally.
     */
    public <A> ListenableFuture<A> submit(DB<A> op)
    {
        return withConnection(op, true);
    }

    /**
     * Submits this operation for execution in the executor service. The operation is executed as a transaction.
     */
    public <A> ListenableFuture<A> transact(DB<A> op)
    {
        return withConnection(transactional(op), false);
    }

    private <A> ListenableFuture<A> withConnection(DB<A> op, boolean autoCommit)
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
