package com.novarto.sanedbc.core.ops;

import fj.F;
import fj.data.Option;
import fj.function.Try1;
import fj.function.Try3;
import fj.function.TryEffect1;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * A set of utility functions related to binder functions, e.g. functions which set prepared statement parameters
 */
public class Binders
{

    /**
     * A prepared statement binder function which does nothing. Used when the query/update statement has no parameters
     */
    public static final TryEffect1<PreparedStatement, SQLException> NO_BINDER = (s) -> {
    };

    /**
     * A binder that takes a binder and an iterable, applies the binder to each element of the iterable and adds it as a batch,
     * executes the batch, and returns the total update count. Used in BatchInsertGenKeysOp and BatchUpdateOp
     * @param binder the binder for a single element in the iterable
     * @param as the iterable
     * @param <A> the type of elements in the iterable
     * @return the total update count, as an Option. The option will be none() iff any of the elements in the update count
     * is equal to Statement.SUCCESS_NO_INFO
     */
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

    /**
     * Creates a binder for an iterable, given a binder for a single element in the iterable. This is useful in cases where
     * you want to issue a single prepared statement to update/insert/delete a collection of elements, instead of utilizing
     * a JDBC batch. Depending on the RDBMS implementation, driver and network latency, this can yield a performance boost in
     * some cases.
     * @param binder a function which takes the current prepared statement parameter index, the prepared statement and the current
     *               iterable element, binds parameters for the current element, and returns the new prepared statement index.
     *
     * @param as the iterable
     * @param <A> the type of elements in the iterable
     * @return a new binder which will bind elements for the whole iterable.
     */
    public static <A> TryEffect1<PreparedStatement, SQLException> iterableBinder(
            Try3<Integer, PreparedStatement, A, Integer, SQLException> binder, Iterable<A> as)
    {
        return ps -> {
            int idx = 1;
            for (A a : as)
            {
                idx = binder.f(idx, ps, a);
            }
        };
    }

}
