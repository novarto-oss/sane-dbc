package com.novarto.sanedbc.core.ops;

import fj.function.Try2;
import fj.function.TryEffect1;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by fmap on 28.11.16.
 */
public class FoldLeftSelectOp<A> extends AbstractSelectOp<A>
{

    private final Try2<A, ResultSet, A, SQLException> fold;
    private final A zero;

    public FoldLeftSelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            Try2<A, ResultSet, A, SQLException> fold, A zero, boolean explain)
    {
        super(sql, binder, explain);
        this.fold = fold;
        this.zero = zero;
    }

    public FoldLeftSelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            Try2<A, ResultSet, A, SQLException> fold, A zero)
    {
        this(sql, binder, fold, zero, false);
    }

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
