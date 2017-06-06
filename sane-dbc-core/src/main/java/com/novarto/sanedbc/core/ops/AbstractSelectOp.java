package com.novarto.sanedbc.core.ops;

import fj.control.db.DB;
import fj.function.TryEffect1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * An abstract SQL 'SELECT' operation which is parametric over its return type. Users will generally use subclasses of
 * this class.
 *
 * Examples of the return type: a collection of objects; a map; a single object aggregating the result set.
 *
 * @param <A> the return type of the operation.
 */
public abstract class AbstractSelectOp<A> extends DB<A>
{
    protected final String sql;
    protected final TryEffect1<PreparedStatement, SQLException> binder;

    /**
     * @param sql the query to execute
     * @param binder a function to bind the PreparedStatement parameters
     */
    public AbstractSelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder)
    {
        this.sql = sql;
        this.binder = binder;
    }

    @Override
    public A run(Connection c) throws SQLException
    {

        try (PreparedStatement s = c.prepareStatement(sql))
        {

            binder.f(s);

            try (final ResultSet rs = s.executeQuery())
            {
                return doRun(rs);
            }

        }
    }

    /**
     * Given a result set, return the operation result
     */
    protected abstract A doRun(ResultSet rs) throws SQLException;

}
