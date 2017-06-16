package com.novarto.sanedbc.core.ops;

import com.novarto.lang.CanBuildFrom;
import fj.F;
import fj.Try;
import fj.control.db.DB;
import fj.data.Option;
import fj.data.Validation;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static fj.data.Option.none;
import static fj.data.Option.some;

/**
 * A set of conversions between DB[A] instances.
 */
public final class DbOps
{

    private DbOps()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Given an existing {@link SelectOp}, converts it to an operation that either returns one result, none at all,
     * or throws if there is more than one result in the result set.
     *
     * This is useful if you are issuing a query by a single primary key. Such a query is never expected to return > 1 results
     * (in which case the returned DB will throw upon interpretation), but can return no results (which is expressed in the
     * return type)
     */
    public static <A> DB<Option<A>> unique(SelectOp<A, ?, ?> op)
    {
        return op.map(xs ->
        {
            Iterator<A> it = xs.iterator();
            if (!it.hasNext())
            {
                return none();
            }

            A result = it.next();

            if (it.hasNext())
            {
                throw new RuntimeException("unique with more than one element");
            }
            return some(result);
        });
    }

    /**
     * Given an existing {@link DB}, converts it to a DB which never throws, provided the encountered error is a subtype of
     * {@link Exception}. Instead, the failure is converted to the desired failure type by calling errTransform, and returned
     * inside a failed {@link Validation} instance.
     *
     * @param db the operation to convert
     * @param errTransform a function that takes an exception and returns an <Err>
     * @param <Err> the desired error type
     * @return a new {@link DB} which upon interpretation returns a validation instance instead of throwing,
     * iff the error is a subtype of {@link Exception}
     */
    public static <A, Err> DB<Validation<Err, A>> toValidation(DB<A> db, F<Exception, Err> errTransform)
    {

        return new DB<Validation<Err, A>>()
        {
            @Override public Validation<Err, A> run(Connection c) throws SQLException
            {
                return Validation.validation(Try.f(() -> db.run(c)).f().toEither().left().map(errTransform::f));
            }
        };
    }


    public static <A> DB<Integer> toChunks(Iterable<A> xs, F<Iterable<A>, DB<Integer>> getOp, int chunkSize)
    {

        return new DB<Integer>()
        {
            @Override public Integer run(Connection c) throws SQLException
            {
                Integer result = 0;

                List<List<A>> chunks = chunks(xs, chunkSize);

                for (List<A> chunk : chunks)
                {
                    result += getOp.f(chunk).run(c);
                }

                return result;
            }
        };

    }

    private static <A> List<List<A>> chunks(Iterable<A> xs, int chunkSize)
    {

        if (chunkSize < 1)
        {
            throw new IllegalArgumentException("chunkSize must be >=1");
        }

        List<List<A>> result = new ArrayList<>();
        List<A> currentList = new ArrayList<>();
        result.add(currentList);
        int count = 0;

        for (A x : xs)
        {

            if (count >= chunkSize)
            {
                currentList = new ArrayList<>();
                result.add(currentList);
                count = 0;
            }

            currentList.add(x);
            count++;

        }

        List<A> last = result.get(result.size() - 1);
        if (last.size() == 0)
        {
            result.remove(result.size() - 1);
        }

        return result;
    }

    /**
     * Given an iterable of DB's, convert it to a single DB of iterable. E.g. List[DB[A]] => DB[List[A]].
     * Utilizes a CanBuildFrom instance to construct the result iterable
     * @param xs the iterable of DB's to convert
     * @param cbf the CanBuildFrom instance
     * @param <A> the type of elements
     * @param <C1> optional intermediate representation, see CanBuildFrom javadoc
     * @param <C2> the type of the result iterable, see CanBuildFrom javadoc
     * @return A DB[C2[A]]
     */
    public static <A, C1 extends Iterable<A>, C2 extends Iterable<A>> DB<C2> sequence(Iterable<DB<A>> xs,
            CanBuildFrom<A, C1, C2> cbf)
    {
        DB<C1> acc = DB.unit(cbf.createBuffer());
        for (DB<A> db : xs)
        {
            DB<C1> fAcc = acc;
            acc = db.bind(x -> fAcc.map(ys -> cbf.add(x, ys)));
        }

        return acc.map(cbf::build);
    }

}
