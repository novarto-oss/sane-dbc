package com.novarto.sanedbc.core.interpreter;

import com.novarto.lang.SneakyThrow;
import fj.control.db.DB;
import fj.function.Try0;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.lift;
import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.transactional;

/**
 * A standard {@link DB} interpreter that utilizes a data source to spawn connections,
 * and submits {@link DB} instances for execution in an ExecutorService. The result is lifted to a CompletableFuture.
 * The CompletableFuture returned will fail iff the underlying DB throws.
 *
 * Spawning of a connection happens inside an executor service thread, the submitted DB operation is executed in the same thread,
 * and then the connection is closed. Therefore, a connection obtained from the pool is accessed by only a single thread
 * before being returned to the pool.
 *
 */
public class AsyncDbInterpreter
{

    private final Try0<Connection, SQLException> ds;
    private final ExecutorService executor;


    public AsyncDbInterpreter(Try0<Connection, SQLException> ds, ExecutorService ex)
    {
        this.ds = ds;
        this.executor = ex;
    }

    public AsyncDbInterpreter(DataSource ds, ExecutorService ex)
    {
        this(lift(ds), ex);
    }


    /**
     * Submits this operation for execution in the executor service. The operation is executed with connection autoCommit = true,
     * i.e. non-transactionally.
     */
    public <A> CompletableFuture<A> submit(DB<A> op)
    {
        return withConnection(op, true);
    }

    /**
     * Submits this operation for execution in the executor service. The operation is executed as a transaction.
     */
    public <A> CompletableFuture<A> transact(DB<A> op)
    {
        return withConnection(transactional(op), false);
    }


    private <A> CompletableFuture<A> withConnection(DB<A> op, boolean autoCommit)
    {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = getConnection(autoCommit))
            {
                return op.run(c);
            }
            catch (Exception e)
            {
                SneakyThrow.sneakyThrow(e);
                throw new IllegalStateException("this code is unreachable");
            }
        }, executor);
    }

    private Connection getConnection(boolean autoCommit) throws SQLException
    {
        Connection result = ds.f();
        result.setAutoCommit(autoCommit);
        return result;
    }




}
