package com.novarto.sanedbc.core;

import fj.function.Effect2;

import java.util.Iterator;

import static com.novarto.sanedbc.core.SqlStringUtils.StatementKind.*;

/**
 * Created by fmap on 06.07.16.
 */
public class SqlStringUtils
{
    public static String placeholders(int length)
    {
        return placeholdersBuilder(length, new StringBuilder()).toString();
    }

    public static StringBuilder placeholdersBuilder(int length, StringBuilder sb)
    {
        if (length < 1)
        {
            throw new IllegalArgumentException();
        }

        for (int i = 0; i < length; i++)
        {
            sb.append("?, ");
        }

        return sb.delete(sb.length() - 2, sb.length());
    }

    public static String placeholderRows(int length, int numColumns)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++)
        {
            sb.append("(");
            sb = placeholdersBuilder(numColumns, sb);
            sb.append("), ");
        }
        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }

    public static String literal(String str)
    {
        return "'" + str + "'";

    }

    public static StringBuilder setExpressionWithPlaceholders(Iterable<String> colNames)
    {
        return expressionWithPlaceholders(colNames, ", ");
    }

    public static StringBuilder whereExpressionWithPlaceholders(Iterable<String> colNames)
    {
        return expressionWithPlaceholders(colNames, " AND ");
    }

    public static StringBuilder expressionWithPlaceholders(Iterable<String> colNames, String separator)
    {
        StringBuilder result = new StringBuilder();

        for (String colName : colNames)
        {
            result.append(colName).append("=?").append(separator);
        }
        return result.delete(result.length() - separator.length(), result.length());
    }

    public static StringBuilder whereExpressionWithNullValues(Iterable<String> columns, Iterable<String> values,
            LogicalOperator logicalOperator)
    {

        final Iterator<String> columnsIterator = columns.iterator();
        final Iterator<String> valuesIterator = values.iterator();
        StringBuilder expression = new StringBuilder();
        while (columnsIterator.hasNext() && valuesIterator.hasNext())
        {

            final String column = columnsIterator.next();
            final String value = valuesIterator.next();
            expression.append(column);

            if (value == null)
            {
                expression.append(" IS ? ");
            }
            else
            {
                expression.append(" = ? ");
            }

            expression.append(logicalOperator.name()).append(" ");

        }

        expression.delete((expression.length() - logicalOperator.name().length() - 1), expression.length());

        return expression;
    }

    public static StatementKind getStatementKind(String sql)
    {

        sql = sql.trim();
        sql = sql.length() < 10 ? sql : sql.substring(0, 10);
        sql = sql.toLowerCase();

        if (sql.startsWith("select"))
        {
            return SELECT;
        }
        else if (sql.startsWith("insert"))
        {
            return INSERT;
        }
        else if (sql.startsWith("update"))
        {
            return UPDATE;
        }
        else if (sql.startsWith("delete"))
        {
            return DELETE;
        }
        else
        {
            return UNKNOWN;
        }

    }

    public enum StatementKind
    {
        SELECT, UPDATE, DELETE, INSERT, UNKNOWN;
    }

    public enum LogicalOperator
    {
        AND, OR
    }

    public static <A> void union(StringBuilder b, Iterable<A> xs, Effect2<A, StringBuilder> singleSql, boolean unionAll)
    {
        String joinClause = unionAll ? " UNION ALL " : " UNION ";
        for (A x : xs)
        {
            b.append("( ");
            singleSql.f(x, b);
            b.append(" )");
            b.append(joinClause);
        }

        int replaceIdx = b.length() - joinClause.length();
        if (replaceIdx < 1)
        {
            return;
        }
        b.replace(replaceIdx, b.length(), "");
    }

    public static <A> StringBuilder union(Iterable<A> xs, Effect2<A, StringBuilder> singleSql, boolean unionAll)
    {
        StringBuilder result = new StringBuilder();
        union(result, xs, singleSql, unionAll);
        return result;
    }

}