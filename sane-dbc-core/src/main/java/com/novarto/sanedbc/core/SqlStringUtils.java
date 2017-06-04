package com.novarto.sanedbc.core;

import fj.function.Effect2;

import java.util.Iterator;

import static com.novarto.sanedbc.core.SqlStringUtils.StatementKind.*;
import static java.text.MessageFormat.format;

/**
 * A set of utility methods to aid you in building SQL queries.
 */
public class SqlStringUtils
{
    /**
     * Generates a number of PreparedStatement placeholders. For example, placeholders(3) will generate "?,?,?"
     * @param length
     * @throws IllegalArgumentException if length<1
     * @return
     */
    public static String placeholders(int length)
    {
        return placeholdersBuilder(length, new StringBuilder()).toString();
    }

    /**
     * A placeholders() version which returns a StringBuilder
     */
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

    /**
     * Generates rows of PreparedStatement placeholders. For example, placeholderRows(4,2) will return
     * (?,?),(?,?),(?,?),(?,?)
     * @param numRows the number of rows to generate
     * @param numColumns the number of columns in each row
     * @return the generated placeholders
     */
    public static String placeholderRows(int numRows, int numColumns)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numRows; i++)
        {
            sb.append("(");
            sb = placeholdersBuilder(numColumns, sb);
            sb.append("), ");
        }
        sb.delete(sb.length() - 2, sb.length());
        return sb.toString();
    }

    /**
     * Surrounds a string with single quotes. This method makes no attempt to prevent SQL injection.
     * It is the caller's responsibility to ensure the parameter is sanitized.
     * @param str
     * @return the quoted string
     */
    public static String literal(String str)
    {
        return "'" + str + "'";

    }

    /**
     * Given a iterable of column names, generates a set expression with placeholders, i.e. one to be used in
     * UPDATE ... SET ... . For example, setExpressionWithPlaceholders(asList("FOO", "BAR")) will yield
     * "FOO=?,BAR=?".
     * This method makes no attempt to escape `colNames`. They should either be passed statically as java literals,
     * or sanitized.
     */
    public static StringBuilder setExpressionWithPlaceholders(Iterable<String> colNames)
    {
        return expressionWithPlaceholders(colNames, ", ");
    }

    /**
     * Given a logical operator (and / or), and an iterable of column names, generates a WHERE expression with placeholders,
     * i.e. one to be used in a WHERE clause. For example, whereExpressionWithPlaceholders(asList("A", "B"), OR)
     * will yield "A=? OR B=?"
     * This method makes no attempt to escape `colNames`. They should either be passed statically as java literals,
     * or sanitized.
     *
     */
    public static StringBuilder whereExpressionWithPlaceholders(Iterable<String> colNames, LogicalOperator op)
    {
        return expressionWithPlaceholders(colNames,
                format(" {0} ", op.name())
        );
    }

    /**
     * Shorthand for whereExpressionWithPlaceholders(colNames, AND)
     */
    public static StringBuilder whereExpressionWithPlaceholders(Iterable<String> colNames)
    {
        return whereExpressionWithPlaceholders(colNames, LogicalOperator.AND);
    }


    private static StringBuilder expressionWithPlaceholders(Iterable<String> colNames, String separator)
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

    /**
     * Tries to detect the type of an sql statement as one of the enums in StatementKind.
     * This method does no lexing or parsing, and almost no normalization, trying to strike a balance
     * between efficiency and utility. It is not intended to be used in application code. Its result should
     * only be interpreted as a hint.
     * @param sql
     * @return a value hinting the statement kind
     */
    public static StatementKind getStatementKind(String sql)
    {

        String prefix = sql.trim();
        prefix = prefix.length() < 10 ? prefix : prefix.substring(0, 10);
        prefix = prefix.toLowerCase();

        if (prefix.startsWith("select"))
        {
            return SELECT;
        }
        else if (prefix.startsWith("insert"))
        {
            return INSERT;
        }
        else if (prefix.startsWith("update"))
        {
            return UPDATE;
        }
        else if (prefix.startsWith("delete"))
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
        SELECT, UPDATE, DELETE, INSERT, UNKNOWN
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