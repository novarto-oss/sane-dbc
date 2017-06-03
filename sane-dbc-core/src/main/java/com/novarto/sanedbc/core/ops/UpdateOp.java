package com.novarto.sanedbc.core.ops;

import fj.control.db.DB;
import fj.function.TryEffect1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UpdateOp extends DB<Integer>
{
    private final String sql;
    private final TryEffect1<PreparedStatement, SQLException> binder;

    public UpdateOp(String sql, TryEffect1<PreparedStatement, SQLException> binder)
    {
        this.sql = sql;
        this.binder = binder;
    }


    @Override
    public Integer run(Connection c) throws SQLException
    {
        try (PreparedStatement s = c.prepareStatement(sql))
        {
            binder.f(s);
            return s.executeUpdate();
        }
    }
}
