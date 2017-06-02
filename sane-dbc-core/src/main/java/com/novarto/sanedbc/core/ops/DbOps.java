package com.novarto.sanedbc.core.ops;

import com.novarto.lang.CanBuildFrom;
import fj.F;
import fj.Try;
import fj.control.db.DB;
import fj.data.Option;
import fj.data.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static fj.data.Option.none;
import static fj.data.Option.some;

/**
 * Created by fmap on 04.07.16.
 */
public final class DbOps
{
    private static final Logger LOGGER = LoggerFactory.getLogger(DbOps.class);

    private DbOps()
    {
        throw new UnsupportedOperationException();
    }

    public static <A> DB<A> rollback(DB<A> op)
    {

        return new DB<A>()
        {
            @Override public A run(Connection c) throws SQLException
            {
                boolean wasAutocommit = c.getAutoCommit();
                if (wasAutocommit)
                {
                    c.setAutoCommit(false);
                }
                try
                {
                    A result = op.run(c);
                    c.commit();
                    return result;
                }
                catch (RuntimeException | SQLException e)
                {
                    c.rollback();
                    throw e;

                }
                catch (Error e)
                {
                    try
                    {
                        c.rollback();
                    }
                    catch (SQLException sqlE)
                    {
                        LOGGER.error("Could not roll back transaction which failed with java.lang.Error", sqlE);
                    }
                    throw e;
                }
                catch (Throwable t)
                {
                    c.rollback();
                    throw t;
                }
                finally
                {
                    if (wasAutocommit)
                    {
                        c.setAutoCommit(true);
                    }
                }

            }
        };
    }

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

    public static <A, Err> DB<Validation<Err, A>> pure(DB<A> db, F<Exception, Err> errTransform)
    {

        return new DB<Validation<Err, A>>()
        {
            @Override public Validation<Err, A> run(Connection c) throws SQLException
            {
                return Validation.validation(Try.f(() -> db.run(c)).f().toEither().left().map(e -> errTransform.f(e)));
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

    public static <A, C1 extends Iterable<A>, C2 extends Iterable<A>> DB<C2> sequence(Iterable<DB<A>> xs,
            CanBuildFrom<A, C1, C2> cbf)
    {
        DB<C1> acc = DB.unit(cbf.createBuffer());
        for (DB<A> db : xs)
        {
            DB<C1> fAcc = acc;
            acc = db.bind(x -> fAcc.map(ys -> cbf.add(x, ys)));
        }

        return acc.map(zs -> cbf.build(zs));
    }

}
