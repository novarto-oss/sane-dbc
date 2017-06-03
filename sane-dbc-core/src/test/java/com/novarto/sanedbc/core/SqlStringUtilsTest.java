package com.novarto.sanedbc.core;

import org.junit.Test;

import static fj.data.List.list;
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
}
