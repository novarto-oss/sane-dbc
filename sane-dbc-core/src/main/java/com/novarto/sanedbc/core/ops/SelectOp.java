package com.novarto.sanedbc.core.ops;

import com.novarto.lang.CanBuildFrom;
import fj.function.Try1;
import fj.function.TryEffect1;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by fmap on 28.06.16.
 */
public class SelectOp<A, C1 extends Iterable<A>, C2 extends Iterable<A>> extends AbstractSelectOp<C2>
{

    private final CanBuildFrom<A, C1, C2> cbf;
    private final Try1<ResultSet, A, SQLException> mapper;

    public SelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            Try1<ResultSet, A, SQLException> mapper, CanBuildFrom<A, C1, C2> cbf, boolean explain)
    {

        super(sql, binder, explain);
        this.mapper = mapper;
        this.cbf = cbf;
    }

    public SelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            Try1<ResultSet, A, SQLException> mapper, CanBuildFrom<A, C1, C2> cbf)
    {
        this(sql, binder, mapper, cbf, false);
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

    public static final class List<A> extends SelectOp<A, java.util.List<A>, java.util.List<A>>
    {

        public List(String sql, TryEffect1<PreparedStatement, SQLException> binder,
                Try1<ResultSet, A, SQLException> mapper)
        {
            super(sql, binder, mapper, CanBuildFrom.listCanBuildFrom());
        }

        public List(String sql, TryEffect1<PreparedStatement, SQLException> binder,
                Try1<ResultSet, A, SQLException> mapper, boolean explain)
        {
            super(sql, binder, mapper, CanBuildFrom.listCanBuildFrom(), explain);
        }
    }

    public static final class FjList<A> extends SelectOp<A, fj.data.List.Buffer<A>, fj.data.List<A>>
    {

        public FjList(String sql, TryEffect1<PreparedStatement, SQLException> binder,
                Try1<ResultSet, A, SQLException> mapper)
        {
            super(sql, binder, mapper, CanBuildFrom.fjListCanBuildFrom());
        }

        public FjList(String sql, TryEffect1<PreparedStatement, SQLException> binder,
                Try1<ResultSet, A, SQLException> mapper, boolean explain)
        {
            super(sql, binder, mapper, CanBuildFrom.fjListCanBuildFrom(), explain);
        }
    }

}
