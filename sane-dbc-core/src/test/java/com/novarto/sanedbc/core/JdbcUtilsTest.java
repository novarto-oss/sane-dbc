package com.novarto.sanedbc.core;

import com.novarto.lang.CanBuildFrom;
import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.*;
import fj.P2;
import fj.Unit;
import fj.control.db.DB;
import fj.data.List;
import fj.data.Option;
import fj.function.TryEffect0;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutionException;

import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;
import static fj.P.p;
import static fj.data.List.arrayList;
import static fj.data.List.list;
import static fj.data.Option.none;
import static fj.data.Option.some;
import static java.text.MessageFormat.format;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class JdbcUtilsTest
{

    private static final SyncDbInterpreter DB = new SyncDbInterpreter(
            () -> DriverManager.getConnection("jdbc:hsqldb:mem:JdbcUtilsTest", "sa", ""));

    @BeforeClass public static void setupSuite()
    {

        DB.transact(new DB<Unit>()
        {
            @Override public Unit run(Connection c) throws SQLException
            {
                try (Statement st = c.createStatement())
                {
                    st.execute("CREATE TABLE MySqlTest_IDS (ID INTEGER PRIMARY KEY IDENTITY, DUMMY CHAR)");
                    st.execute("CREATE TABLE MySqlTest_DATA (ID INTEGER PRIMARY KEY, DESCRIPTION VARCHAR(100))");

                    st.execute("CREATE TABLE MySqlTest_EXPIRING_STUFF (ID INTEGER PRIMARY KEY IDENTITY," +
                            " DESCRIPTION VARCHAR(100), STAMP BIGINT)");
                    st.execute("CREATE TABLE MySqlTest_FOO (X VARCHAR (100), Y VARCHAR(100))");


                    return Unit.unit();
                }
            }
        });
    }

    private static BatchInsertGenKeysOp.FjList<String, Integer> insertKeysOp(Iterable<String> dummys)
    {
        return new BatchInsertGenKeysOp.FjList<>("INSERT INTO MySqlTest_IDS(DUMMY) VALUES (?)",
                x -> st -> st.setString(1, x), dummys, rs -> rs.getInt(1));
    }

    private static BatchUpdateOp<P2<Integer, String>> insertDataOp(Iterable<P2<Integer, String>> data)
    {
        return new BatchUpdateOp<>("INSERT INTO MySqlTest_DATA VALUES (?, ?)", x -> st ->
        {
            st.setInt(1, x._1());
            st.setString(2, x._2());
        }, data);
    }

    private static final SelectOp.FjList<String> SELECT_ALL_DATA_OP = new SelectOp.FjList<>("SELECT * FROM MySqlTest_DATA",
            NO_BINDER, rs -> rs.getString(2));

    private static final SelectOp.FjList<String> SELECT_ALL_IDS_OP = new SelectOp.FjList<>("SELECT * FROM MySqlTest_IDS",
            NO_BINDER, rs -> rs.getString(2));

    private static SelectOp.FjList<String> selectByDescOp(String desc)
    {
        return new SelectOp.FjList<>("SELECT * FROM MySqlTest_DATA WHERE DESCRIPTION=?",
                (preparedStatement) -> preparedStatement.setString(1, desc), resultSet -> resultSet.getString(2));
    }

    private static DB<Option<String>> selectByUniqueDescOp(String desc)
    {
        return DbOps.unique(new SelectOp.FjList<>("SELECT * FROM MySqlTest_DATA WHERE DESCRIPTION=?",
                (preparedStatement) -> preparedStatement.setString(1, desc), resultSet -> resultSet.getString(2)));
    }

    private static final AggregateOp COUNT_IDS = new AggregateOp("SELECT COUNT(*) FROM MySqlTest_IDS");
    private static final AggregateOp COUNT_DATA = new AggregateOp("SELECT COUNT(*) FROM MySqlTest_DATA");

    private static UpdateOp insertSingleChar(String dummy)
    {
        return new UpdateOp("INSERT INTO MySqlTest_IDS(DUMMY) VALUES (?)", (stmt) -> stmt.setString(1, dummy));
    }

    @After public void cleanup()
    {
        DB.transact(new DB<Unit>()
        {
            @Override public Unit run(Connection c) throws SQLException
            {
                try (Statement st = c.createStatement())
                {
                    st.execute("DELETE FROM MySqlTest_IDS");
                    st.execute("DELETE FROM MySqlTest_DATA");
                    st.execute("DELETE FROM MySqlTest_EXPIRING_STUFF");
                    st.execute("DELETE FROM MySqlTest_FOO");


                    return Unit.unit();
                }
            }
        });
    }

    @Test public void chainedTransaction()
    {

        DB<List<String>> readInserted = insertKeysOp(asList("a", "b", "c")).bind(ids ->
        {
            List<P2<Integer, String>> data = ids.zip(list("Pesho", "Gosho", "Dragan"));
            return insertDataOp(data).bind(whatever -> SELECT_ALL_DATA_OP);
        });

        List<String> result = DB.transact(readInserted);
        assertThat(result, is(list("Pesho", "Gosho", "Dragan")));

    }

    @Test public void rollback()
    {

        DB<List<Long>> insertDataFail = insertKeysOp(list("okidoki")).bind(ids -> fail("failed i have"));

        swallow(() -> DB.transact(insertDataFail));

        DB<P2<Long, Long>> tryCounts = COUNT_IDS.bind(idCount -> COUNT_DATA.map(dataCount -> p(idCount, dataCount)));

        P2<Long, Long> counts = DB.submit(tryCounts);

        assertEquals(0, (long) counts._1());
        assertEquals(0, (long) counts._2());
    }

    @Test public void rollback2()
    {

        DB<Unit> failedInsert = insertKeysOp(list("ok"))
                .bind(ids -> insertDataOp(ids.zip(list("description"))).bind(insertCount ->
                {
                    throw new RuntimeException("failed I have");
                }));

        swallow(() -> DB.transact(failedInsert));

        DB<P2<Long, Long>> tryCounts = COUNT_IDS.bind(idCount -> COUNT_DATA.map(dataCount -> p(idCount, dataCount)));

        P2<Long, Long> counts = DB.submit(tryCounts);

        assertEquals(0, (long) counts._1());
        assertEquals(0, (long) counts._2());
    }

    @Test public void selectBy()
    {

        DB<Unit> insertIt = insertKeysOp(list("a"))
                .bind(ids -> insertDataOp(ids.zip(list("my_description"))).map(ignore -> Unit.unit()));
        DB.submit(insertIt);

        List<String> descriptions = DB.submit(selectByDescOp("my_description"));
        assertEquals(list("my_description"), descriptions);

    }

    @Test public void shouldDoInsertsAsUpdateOperations() throws ExecutionException, InterruptedException
    {

        DB<List<String>> insertSelect = insertSingleChar("a").bind(rc1 -> insertSingleChar("b").bind(rc2 -> SELECT_ALL_IDS_OP));

        List<String> result = DB.submit(insertSelect);

        assertEquals(list("a", "b"), result);
    }

    @Test public void shouldSupportWhereSelects() throws ExecutionException, InterruptedException
    {

        //CREATE TABLE MySqlTest_EXPIRING_STUFF (ID INTEGER PRIMARY KEY AUTO_INCREMENT, DESCRIPTION VARCHAR(100),STAMP BIGINT)"
        UpdateOp insert = new UpdateOp("INSERT INTO MySqlTest_EXPIRING_STUFF(DESCRIPTION,STAMP) VALUES (?,?)", (stmt) ->
        {
            stmt.setString(1, "a");
            stmt.setLong(2, 1L);
        });

        final DB<List<String>> selectOp = insert
                .bind(ignore -> new SelectOp.FjList<>("SELECT * FROM MySqlTest_EXPIRING_STUFF WHERE STAMP < ? ",
                        (stmt) -> stmt.setLong(1, 2L), resultSet -> resultSet.getString(2)));

        List<String> result = DB.submit(selectOp);

        assertEquals(list("a"), result);
    }

    @Test public void foldLeft()
    {
        FoldLeftSelectOp<Integer> fold = new FoldLeftSelectOp<>("SELECT * FROM MySqlTest_IDS", NO_BINDER,
                (x, rs) -> x + rs.getInt(1), 0);
        Integer result = DB.submit(fold);
        assertThat(result, is(0));

        DB.submit(insertKeysOp(asList("a", "b", "c", "d")));

        Integer expected = DB.submit(new SelectOp.FjList<>("SELECT ID FROM MySqlTest_IDS", NO_BINDER, x -> x.getInt(1)))
                .foldLeft((x, y) -> x + y, 0);
        result = DB.submit(fold);
        assertThat(result, is(expected));

    }

    @Test(expected = RuntimeException.class)
    public void toChunksLabdaThrows() throws SQLException
    {
        fj.control.db.DB<Integer> db = DbOps.toChunks(list(1, 2, 3), x ->
        {
            throw new RuntimeException();

        }, 2).bind(x -> fj.control.db.DB.unit(3));


        db.run(null);
    }

    @Test
    public void chunkEmptyIterable() throws SQLException
    {
        DB<Integer> db = DbOps.toChunks(List.<Integer>list(), xs -> {
           throw new RuntimeException();
        }, 10);

        Integer result = db.run(null);
        assertThat(result, is(0));
    }

    @Test
    public void insertGenKeys()
    {
        Integer id = DB.transact(new InsertGenKeysOp.Int(
                "INSERT INTO MySqlTest_IDS(DUMMY) VALUES (?)",
                ps -> ps.setString(1, "a")));

        assertThat(id, is(notNullValue()));

        String str = DB.submit(DbOps
                .unique(new SelectOp.List<>("SELECT DUMMY FROM MySqlTest_IDS WHERE ID=?", ps -> ps.setInt(1, id),
                        rs -> rs.getString(1)))).some();

        assertThat(str, is("a"));
    }

    @Test
    public void submitIsNotTransactional()
    {
        DB<Unit> fail = insertKeysOp(arrayList("a", "b", "c")).bind(ignore -> new DB<Unit>()
        {
            @Override public Unit run(Connection c) throws SQLException
            {
                throw new SQLException("failed i have");
            }
        });

        swallow(() -> DB.submit(fail));

        assertThat(DB.submit(SELECT_ALL_IDS_OP), is(arrayList("a", "b", "c")));
        assertThat(DB.transact(SELECT_ALL_IDS_OP), is(arrayList("a", "b", "c")));

    }

    @Test
    public void iterableBinder()
    {
        List<P2<String, String>> data = arrayList(p("x1", "y1"), p("x2", "y2"), p("x3", "y3"));
        String sql = format(
                "INSERT INTO MySqlTest_FOO(X,Y) VALUES ({0})",
                SqlStringUtils.placeholderRows(data.length(), 2)
        );

        UpdateOp insert = new UpdateOp(
                sql,
                Binders.iterableBinder((pos, ps, x) -> {
                    int currentPosition = pos;
                    ps.setString(currentPosition++, x._1());
                    ps.setString(currentPosition++, x._2());
                    return currentPosition;

                }, data)
        );

        int updateCount = DB.transact(insert);
        assertThat(updateCount, is(3));

        List<P2<String, String>> readData = DB.submit(new SelectOp.FjList<>("SELECT X,Y FROM MySqlTest_FOO", NO_BINDER,
                rs -> p(rs.getString(1), rs.getString(2))));

        assertThat(readData, is(data));
    }

    @Test public void dbSequence()
    {
        DB.transact(insertDataOp(arrayList(p(1, "foo"), p(2, "bar"), p(3, "zzz"))));

        List<DB<Option<String>>> dbs = arrayList(selectByUniqueDescOp("foo"), selectByUniqueDescOp("zzz"),
                selectByUniqueDescOp("no_such"));

        List<Option<String>> result = DB.submit(DbOps.sequence(dbs, CanBuildFrom.fjListCanBuildFrom()));
        assertThat(result, is(arrayList(some("foo"), some("zzz"), none())));
    }

    private void swallowChecked(TryEffect0<?> f)
    {
        try
        {
            f.f();
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            //ok
        }
    }

    private void swallow(TryEffect0<?> f)
    {
        try
        {
            f.f();
        }
        catch (Exception e)
        {
            //ok
        }
    }

    public static <A> DB<A> fail(String msg)
    {
        return new DB<A>()
        {
            @Override public A run(Connection connection) throws SQLException
            {
                throw new RuntimeException(msg);
            }
        };
    }

}
