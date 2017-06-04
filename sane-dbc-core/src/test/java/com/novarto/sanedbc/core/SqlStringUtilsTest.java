package com.novarto.sanedbc.core;

import org.junit.Test;

import static com.novarto.sanedbc.core.SqlStringUtils.LogicalOperator.AND;
import static com.novarto.sanedbc.core.SqlStringUtils.LogicalOperator.OR;
import static com.novarto.sanedbc.core.SqlStringUtils.StatementKind.*;
import static fj.data.List.list;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SqlStringUtilsTest
{
    @Test
    public void union()
    {
        StringBuilder result = SqlStringUtils.<String>union(list(), (x, b) -> b.append("hi " + x), true);
        assertThat(result.toString(), is(""));

        result = SqlStringUtils.union(list("a"), (x, b) -> b.append("hi " + x), true);
        assertThat(result.toString(), is("( hi a )"));

        result = SqlStringUtils.union(list("a", "b", "c"), (x, b) -> b.append("hi " + x), false);
        assertThat(result.toString(), is("( hi a ) UNION ( hi b ) UNION ( hi c )"));

        result = SqlStringUtils.union(list("x", "y"), (x, b) -> b.append("hi " + x), true);
        assertThat(result.toString(), is("( hi x ) UNION ALL ( hi y )"));

    }

    @Test
    public void placeholders()
    {
        assertThat(SqlStringUtils.placeholders(1), is("?"));
        assertThat(SqlStringUtils.placeholders(5).replaceAll(" ", ""),
                is("?,?,?,?,?"));

    }

    @Test
    public void placeholderRows()
    {
        assertThat(SqlStringUtils.placeholderRows(3, 2).replaceAll(" ", ""),
                is("(?,?),(?,?),(?,?)"));
    }

    @Test
    public void literal()
    {
        assertThat(SqlStringUtils.literal("foo"), is("'foo'"));
    }

    @Test
    public void setExpressionWithPlaceholders()
    {
        assertThat(
                SqlStringUtils.setExpressionWithPlaceholders(asList("CITY", "ADDRESS")).toString()
                        .replaceAll(" ", ""),
                is("CITY=?,ADDRESS=?")
        );
    }

    @Test
    public void whereExpressionWithPlaceholders()
    {
        assertThat(
                SqlStringUtils.whereExpressionWithPlaceholders(asList("CITY", "ADDRESS"), AND).toString(),
                is("CITY=? AND ADDRESS=?")
        );

        assertThat(
                SqlStringUtils.whereExpressionWithPlaceholders(asList("X", "Y", "Z"), OR).toString(),
                is("X=? OR Y=? OR Z=?")
        );
    }

    @Test
    public void statementKind()
    {
        assertThat(
                SqlStringUtils.getStatementKind("                           SELECT * FROM FOO"),
                is(SELECT)
        );

        assertThat(
                SqlStringUtils.getStatementKind("insert into alabalanicafoorbar values ()"),
                is(INSERT)
        );

        assertThat(SqlStringUtils.getStatementKind("update x set y=? where z=?"),
                is(UPDATE)
        );

        assertThat(SqlStringUtils.getStatementKind("DELETE from x"),
                is(DELETE)
        );

        assertThat(SqlStringUtils.getStatementKind("DROP SCHEMA TAXES"),
                is(UNKNOWN)
        );
    }


}
