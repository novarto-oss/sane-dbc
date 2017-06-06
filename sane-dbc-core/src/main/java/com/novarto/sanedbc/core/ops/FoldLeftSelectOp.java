package com.novarto.sanedbc.core.ops;

import fj.function.Try2;
import fj.function.TryEffect1;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A select operation which folds (i.e. reduces) the result set to a single result
 * @param <A> the type of the result
 */
public class FoldLeftSelectOp<A> extends AbstractSelectOp<A>
{

    private final Try2<A, ResultSet, A, SQLException> fold;
    private final A zero;

    /**
     *
     * @param sql the query to execute
     * @param binder a function to bind the PreparedStatement parameters
     * @param fold a function from the result so far and a ResultSet row to the next result. The function
     *               must not advance or modify the ResultSet state, i.e. by calling next()
     * @param zero the zero of the fold, i.e. the initial value of the reduction
     */
    public FoldLeftSelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            Try2<A, ResultSet, A, SQLException> fold, A zero)
    {
        super(sql, binder);
        this.fold = fold;
        this.zero = zero;
    }

    /**
     * Iterates the ResultSet row by row, performs the reduction and returns the result.
     */
    @Override protected A doRun(ResultSet rs) throws SQLException
    {
        A result = zero;
        while (rs.next())
        {
            result = fold.f(result, rs);
        }
        return result;
    }
}
