package com.novarto.sanedbc.core.ops;

import com.novarto.sanedbc.core.ResultParser;
import fj.control.db.DB;
import fj.function.TryEffect1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractSelectOp<A> extends DB<A>
{
    protected static final Logger LOGGER = LoggerFactory.getLogger(SelectOp.class);

    protected final String sql;
    protected final TryEffect1<PreparedStatement, SQLException> binder;
    protected final boolean explain;

    public AbstractSelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder,
            boolean explain)
    {
        this.sql = sql;
        this.binder = binder;
        this.explain = explain;
    }

    public AbstractSelectOp(String sql, TryEffect1<PreparedStatement, SQLException> binder)
    {
        this(sql, binder, false);
    }

    @Override
    public A run(Connection c) throws SQLException
    {

        if (explain)
        {
            LOGGER.info(explainIt(c));
        }

        try (PreparedStatement s = c.prepareStatement(sql))
        {

            final long start = System.currentTimeMillis();
            binder.f(s);

            try (final ResultSet rs = s.executeQuery())
            {
                A result = doRun(rs);
                if (explain)
                {
                    LOGGER.info("Execution of query {} took: {}", sql, (System.currentTimeMillis() - start));
                }
                return result;

            }



        }
    }

    protected abstract A doRun(ResultSet rs) throws SQLException;

    private String explainIt(Connection c) throws SQLException
    {
        try (Statement s = c.createStatement())
        {
            String explainSql = "EXPLAIN " + sql;
            ResultSet rs = s.executeQuery(explainSql);

            java.util.List<LinkedHashMap<String, Object>> result = ResultParser.parseResultSet(rs);
            StringBuilder sb = new StringBuilder();

            sb.append("EXPLAIN for query: ").append(sql).append(System.lineSeparator());

            for (LinkedHashMap<String, Object> row : result)
            {
                for (Map.Entry<String, Object> col : row.entrySet())
                {
                    sb.append(col.getKey()).append("-->").append(col.getValue()).append(" | ");
                }
                sb.append(System.lineSeparator());
            }

            return sb.toString();
        }

    }

}
