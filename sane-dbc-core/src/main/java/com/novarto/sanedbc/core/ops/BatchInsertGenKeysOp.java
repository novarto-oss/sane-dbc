package com.novarto.sanedbc.core.ops;

import com.novarto.lang.CanBuildFrom;
import com.novarto.lang.Collections;
import fj.F;
import fj.control.db.DB;
import fj.data.Option;
import fj.function.Try1;
import fj.function.TryEffect1;

import java.sql.*;

import static com.novarto.sanedbc.core.ops.Binders.batchBinder;

/**
 * Batch insert operation which returns the auto-generated keys created for the inserted entries. Please consider that the used
 * JDBC driver may not support the retrieval of auto-generated key and this operation may fail with {@link
 * SQLFeatureNotSupportedException}
 *
 * @param <A>  Type of single entry to be inserted
 * @param <B>  Type of the result which must extends {@link Number}
 * @param <C1> The type of the optional (mutable) buffer which is intermediately used while constructing the result.
 * @param <C2> Type of the result
 * @see Statement#getGeneratedKeys()
 */
public abstract class BatchInsertGenKeysOp<A, B extends Number, C1 extends Iterable<B>, C2 extends Iterable<B>> extends DB<C2>

{
    private final String sql;
    private final Try1<PreparedStatement, Option<Integer>, SQLException> binder;
    private final Try1<ResultSet, B, SQLException> getKey;
    private final CanBuildFrom<B, C1, C2> cbf;
    private final Iterable<A> as;

    public BatchInsertGenKeysOp(String sql, F<A, TryEffect1<PreparedStatement, SQLException>> binder, Iterable<A> as,
            Try1<ResultSet, B, SQLException> getKey, CanBuildFrom<B, C1, C2> cbf)
    {
        this.sql = sql;
        this.binder = batchBinder(binder, as);
        this.getKey = getKey;
        this.cbf = cbf;
        this.as = as;
    }

    @Override
    public C2 run(Connection c) throws SQLException
    {
        if (Collections.isEmpty(as))
        {
            return cbf.build(cbf.createBuffer());
        }

        try (PreparedStatement preparedStatement = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {

            binder.f(preparedStatement);

            C1 buf = cbf.createBuffer();
            try (final ResultSet generatedKeys = preparedStatement.getGeneratedKeys())
            {
                while (generatedKeys.next())
                {

                    buf = cbf.add(getKey.f(generatedKeys), buf);
                }

                if (Collections.isEmpty(buf))
                {
                    throw new SQLException("No Auto Generated Keys in Result Set!");
                }

                return cbf.build(buf);
            }

        }
    }

    public static class List<A, B extends Number> extends BatchInsertGenKeysOp<A, B, java.util.List<B>, java.util.List<B>>
    {

        public List(String sql, F<A, TryEffect1<PreparedStatement, SQLException>> binder, Iterable<A> as,
                Try1<ResultSet, B, SQLException> getKey)
        {
            super(sql, binder, as, getKey, CanBuildFrom.listCanBuildFrom());
        }
    }

    public static class FjList<A, B extends Number> extends BatchInsertGenKeysOp<A, B, fj.data.List.Buffer<B>, fj.data.List<B>>
    {

        public FjList(String sql, F<A, TryEffect1<PreparedStatement, SQLException>> binder, Iterable<A> as,
                Try1<ResultSet, B, SQLException> getKey)
        {
            super(sql, binder, as, getKey, CanBuildFrom.fjListCanBuildFrom());
        }
    }

}
