package com.novarto.sanedbc.core.ops;

import com.novarto.lang.CanBuildFrom;
import fj.function.Try1;
import fj.function.TryEffect1;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A select operation which returns an iterable of results. The iterable is constructed via a CanBuildFrom instance
 * @param <A> The type of a single element in the result iterable
 * @param <C1> The type of the optional (mutable) buffer which is intermediately used while constructing the result.
 * @param <C2> The type of the result
 */
public class SelectOp<A, C1 extends Iterable<A>, C2 extends Iterable<A>> extends AbstractSelectOp<C2>
{

    private final CanBuildFrom<A, C1, C2> cbf;
    private final Try1<ResultSet, A, SQLException> mapper;

    /**
     *
     * @param sql the query to execute
     * @param binder a function to bind the PreparedStatement parameters
     * @param mapper a function mapping a single ResultSet row to a single element of the result iterable. The function
     *               must not advance or modify the ResultSet state, i.e. by calling next()
     * @param cbf the CanBuildFrom to construct the result iterable
     */
    public SelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            Try1<ResultSet, A, SQLException> mapper, CanBuildFrom<A, C1, C2> cbf)
    {

        super(sql, binder);
        this.mapper = mapper;
        this.cbf = cbf;
    }

    @Override protected C2 doRun(ResultSet rs) throws SQLException
    {
        C1 buf = cbf.createBuffer();

        while (rs.next())
        {
            buf = cbf.add(mapper.f(rs), buf);
        }


        return cbf.build(buf);
    }

    /**
     * A convenience SelectOp specialized for java.util.List
     */
    public static final class List<A> extends SelectOp<A, java.util.List<A>, java.util.List<A>>
    {

        public List(String sql, TryEffect1<PreparedStatement, SQLException> binder,
                Try1<ResultSet, A, SQLException> mapper)
        {
            super(sql, binder, mapper, CanBuildFrom.listCanBuildFrom());
        }

    }

    /**
     * A convenience SelectOp specialized for fj.data.List, utilizing a fj.data.List.Buffer as an intermediate result
     */
    public static final class FjList<A> extends SelectOp<A, fj.data.List.Buffer<A>, fj.data.List<A>>
    {

        public FjList(String sql, TryEffect1<PreparedStatement, SQLException> binder,
                Try1<ResultSet, A, SQLException> mapper)
        {
            super(sql, binder, mapper, CanBuildFrom.fjListCanBuildFrom());
        }

    }

}
