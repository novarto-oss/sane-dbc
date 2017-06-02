package com.novarto.sanedbc.core.ops;

import fj.control.db.DB;
import fj.function.Try2;
import fj.function.TryEffect1;

import java.sql.*;

/**
 * Created by fmap on 28.06.16.
 */
public abstract class InsertGenKeysOp<A extends Number> extends DB<A>

{
    private final String sql;
    private final TryEffect1<PreparedStatement, SQLException> binder;
    private final Try2<ResultSet, Integer, A, SQLException> getKey;

    public InsertGenKeysOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            Try2<ResultSet, Integer, A, SQLException> getKey)
    {
        this.sql = sql;
        this.binder = binder;
        this.getKey = getKey;
    }

    @Override
    public A run(Connection c) throws SQLException
    {
        try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            binder.f(s);
            int insertedRowsCount = s.executeUpdate();
            if (insertedRowsCount < 1)
            {
                throw new SQLException("No rows inserted!");
            }

            final ResultSet generatedKeys = s.getGeneratedKeys();
            if (generatedKeys.next())
            {
                return getKey.f(generatedKeys, 1);
            }
            else
            {
                throw new SQLException("No Auto Generated Keys in Result Set!");
            }

        }
    }

    public static class Long extends InsertGenKeysOp<java.lang.Long>
    {

        public Long(String sql, TryEffect1<PreparedStatement, SQLException> binder)
        {
            super(sql, binder, (rs, pos) -> rs.getLong(pos));
        }

    }

    public static class Int extends InsertGenKeysOp<Integer>
    {

        public Int(String sql, TryEffect1<PreparedStatement, SQLException> binder)
        {
            super(sql, binder, (rs, pos) -> rs.getInt(pos));
        }

    }

    public static class Short extends InsertGenKeysOp<java.lang.Short>
    {

        public Short(String sql, TryEffect1<PreparedStatement, SQLException> binder)
        {
            super(sql, binder, (rs, pos) -> rs.getShort(pos));
        }

    }

    public static class Byte extends InsertGenKeysOp<java.lang.Byte>
    {

        public Byte(String sql, TryEffect1<PreparedStatement, SQLException> binder)
        {
            super(sql, binder, (rs, pos) -> rs.getByte(pos));
        }

    }
}
