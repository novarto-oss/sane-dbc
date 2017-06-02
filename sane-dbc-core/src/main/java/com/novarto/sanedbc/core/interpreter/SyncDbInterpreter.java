package com.novarto.sanedbc.core.interpreter;

import fj.control.db.DB;
import fj.function.Try0;

import java.sql.Connection;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.ops.DbOps.*;

public class SyncDbInterpreter
{
    private final Try0<Connection, SQLException> ds;

    public SyncDbInterpreter(Try0<Connection, SQLException> ds)
    {
        this.ds = ds;
    }

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

    public <A> A transact(DB<A> doOp)
    {
        try (Connection c = ds.f())
        {
            return rollback(doOp).run(c);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }

    }
}
