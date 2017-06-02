package com.novarto.sanedbc.core.ops;

import com.novarto.sanedbc.core.SqlStringUtils;
import fj.F;
import fj.control.db.DB;
import fj.data.Option;
import fj.function.Try1;
import fj.function.TryEffect1;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.ops.Binders.batchBinder;

/**
 * Created by fmap on 28.06.16.
 */
public class BatchUpdateOp<A> extends DB<Option<Integer>>
{
    private final String sql;
    private final Try1<PreparedStatement, Option<Integer>, SQLException> binder;

    public BatchUpdateOp(String sql, F<A, TryEffect1<PreparedStatement, SQLException>> binder, Iterable<A> as)
    {
        this.binder = batchBinder(binder, as);
        this.sql = sql;
    }

    @Override
    public Option<Integer> run(Connection c) throws SQLException
    {
        if (!isInsert(sql))
        {
            throwIfAutoCommit(c);
        }
        try (PreparedStatement preparedStatement = c.prepareStatement(sql))
        {

            return binder.f(preparedStatement);
        }
    }

    public static void throwIfAutoCommit(Connection c) throws SQLException
    {
        if (c.getAutoCommit())
        {
            throw new RuntimeException("you are performing a batch update or delete operation, but auto commit is true." +
                    "This results in a drastic performance degradation under mysql, so i'm just going to stop you." +
                    "Use com.novarto.rubik.rulesdb.client.core.db.MySQL.withTransaction or " +
                    "com.novarto.test.db.DBTest.transact");
        }

    }

    private boolean isInsert(String sql)
    {
        return SqlStringUtils.getStatementKind(sql).equals(SqlStringUtils.StatementKind.INSERT);
    }
}
