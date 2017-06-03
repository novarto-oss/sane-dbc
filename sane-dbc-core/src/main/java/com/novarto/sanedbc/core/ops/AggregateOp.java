package com.novarto.sanedbc.core.ops;

import fj.control.db.DB;
import fj.function.TryEffect1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;

import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;

public class AggregateOp extends DB<Long>
{

    private final SelectOp.List<Long> selectOp;

    public AggregateOp(String sql, TryEffect1<PreparedStatement, SQLException> binder)
    {
        this.selectOp = new SelectOp.List<>(sql, binder, x -> x.getLong(1));
    }

    public AggregateOp(String sql)
    {
        this(sql, NO_BINDER);
    }

    @Override public Long run(Connection c) throws SQLException
    {
        return selectOp.map(xs ->
        {

            Iterator<Long> resultIterator = xs.iterator();
            if (!resultIterator.hasNext())
            {
                throw new IllegalStateException("result is empty");

            }
            Long result = resultIterator.next();

            if (resultIterator.hasNext())
            {
                throw new IllegalStateException("result has more than one row");
            }
            return result;
        }).run(c);
    }

}
