package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.ops.SelectOp;
import org.junit.Test;

import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;

public class DescribeVsInterpret
{
    @Test
    public void nothingHappens()
    {
        @SuppressWarnings("unused")
        SelectOp.FjList<String> selectThem = new SelectOp.FjList<>(
                "SELECT FOO FROM WHATEVER",
                NO_BINDER,
                rs -> rs.getString(1)
        );
    }
}
