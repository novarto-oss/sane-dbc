package com.novarto.sanedbc.core.ops;

import fj.Unit;
import fj.control.db.DB;

import java.sql.Connection;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;

/**
 * A db operation which takes no parameters, and returns nothing. One usecase for this is executing DDL
 */
public class EffectOp extends DB<Unit>
{

    private final UpdateOp op;

    public EffectOp(String sql)
    {
        this.op = new UpdateOp(sql, NO_BINDER);
    }

    @Override public Unit run(Connection c) throws SQLException
    {
        op.run(c);
        return Unit.unit();
    }
}
