package com.novarto.sanedbc.core.ops;

import fj.F;
import fj.F2;
import fj.data.Option;
import fj.function.Try1;
import fj.function.Try3;
import fj.function.TryEffect1;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class Binders
{

    public static final TryEffect1<PreparedStatement, SQLException> NO_BINDER = (s) -> {
    };

    public static <A> TryEffect1<PreparedStatement, SQLException> iterableBinder(
            F2<A, Integer, Try1<PreparedStatement, Integer, SQLException>> binder, Iterable<A> xs)
    {
        return ps -> {
            int pos = 1;
            for (A a : xs)
            {
                pos = binder.f(a, pos).f(ps);
            }
        };
    }

    public static <A> Try1<PreparedStatement, Option<Integer>, SQLException> batchBinder(
            F<A, TryEffect1<PreparedStatement, SQLException>> binder, Iterable<A> as)
    {
        return ps -> {
            for (A a : as)
            {
                binder.f(a).f(ps);
                ps.addBatch();
            }
            return sumBatchResult(ps.executeBatch());

        };
    }

    private static Option<Integer> sumBatchResult(int[] xs)
    {
        int result = 0;
        for (int x: xs)
        {
            if (x >= 0)
            {
                result += x;
            }
            else if (x == Statement.SUCCESS_NO_INFO)
            {
                return Option.none();
            }
            else if (x == Statement.EXECUTE_FAILED)
            {
                throw new IllegalStateException("a batch command failed but an sql exception was not raised by the driver!");
            }
            else
            {
                throw new IllegalStateException("unrecognized batch return code " + x);
            }
        }
        return Option.some(result);
    }

    public static <A> Try1<PreparedStatement, Integer, SQLException> optimizedBatchBinder(
            Try3<Integer, PreparedStatement, A, Integer, SQLException> binder, Iterable<A> as)
    {
        return ps -> {
            int idx = 1;
            for (A a : as)
            {
                idx = binder.f(idx, ps, a);
            }
            return idx;
        };
    }

}
