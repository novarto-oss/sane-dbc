package com.novarto.sanedbc.core.ops.optimized;

import com.novarto.sanedbc.core.SqlStringUtils;
import com.novarto.sanedbc.core.ops.DbOps;
import com.novarto.lang.Collections;
import com.novarto.lang.StringUtil;
import fj.control.db.DB;
import fj.function.Try1;
import fj.function.Try3;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.List;

import static com.novarto.sanedbc.core.ops.Binders.optimizedBatchBinder;

/**
 * Created by fmap on 21.07.16.
 */
public class BatchDeleteOptimized<A> extends DB<Integer>
{

    private static final String SQL_TEMPLATE = "DELETE FROM {0} WHERE ({1}) IN ({2})";

    private final DB<Integer> op;

    public BatchDeleteOptimized(String tableName, List<String> whereColumns, Iterable<A> xs,
            Try3<Integer, PreparedStatement, A, Integer, SQLException> binder, int batchSize)
    {

        int whereColumnsLength = whereColumns.size();

        op = DbOps.toChunks(xs, ys -> {
            String colsSegment = "(" + StringUtil.join(whereColumns, ", ") + ")";

            String sql = MessageFormat.format(SQL_TEMPLATE, tableName, colsSegment,
                    SqlStringUtils.placeholderRows(Collections.size(xs), whereColumnsLength));

            Try1<PreparedStatement, Integer, SQLException> populatedBinder = optimizedBatchBinder(binder, xs);

            return new DB<Integer>()
            {

                @Override
                public Integer run(Connection c) throws SQLException
                {
                    try (PreparedStatement ps = c.prepareStatement(sql))
                    {
                        populatedBinder.f(ps);
                        return ps.executeUpdate();
                    }
                }
            };
        }, batchSize);

    }

    @Override
    public Integer run(Connection c) throws SQLException
    {
        return op.run(c);
    }
}
