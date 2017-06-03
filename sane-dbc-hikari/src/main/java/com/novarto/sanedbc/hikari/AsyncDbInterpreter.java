package com.novarto.sanedbc.hikari;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import fj.control.db.DB;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.ops.DbOps.rollback;

public class AsyncDbInterpreter
{

    private final DataSource ds;
    private final ListeningExecutorService executor;


    public AsyncDbInterpreter(DataSource ds, ListeningExecutorService ex)
    {
        this.ds = ds;
        this.executor = ex;
    }


    public <A> ListenableFuture<A> withConnection(DB<A> op, boolean autoCommit)
    {
        return executor.submit(() ->
        {

            try (Connection c = getConnection(autoCommit))
            {
                return op.run(c);
            }
        });
    }

    public <A> ListenableFuture<A> withConnection(DB<A> op)
    {
        return withConnection(op, true);
    }

    public <A> ListenableFuture<A> withTransaction(DB<A> op)
    {

        return withConnection(rollback(op), false);
    }

    /**
     * Public to be reused by other DB interpreters, use with caution.
     *
     * @param autoCommit
     * @return
     * @throws SQLException
     */
    public Connection getConnection(boolean autoCommit) throws SQLException
    {
        final Connection connection = ds.getConnection();
        connection.setAutoCommit(autoCommit);
        return connection;
    }


}
