package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.*;
import fj.Unit;
import fj.control.db.DB;
import fj.data.List;
import fj.data.Option;
import org.junit.Test;

import java.sql.DriverManager;

import static com.novarto.sanedbc.core.ops.DbOps.unique;
import static fj.data.List.nil;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class BasicUsage
{

    /*
     * In a real-world application, DB-related operations are usually encapsulated in a separate module, so that's what we'll do here.
     * It's kind of like a DAO (Data Access Object) since it provides operations over a table, or a set of logically related tables.
     *
     * It's different from a DAO in that it does not access the database itself; rather it returns descriptions of how it should
     * be accessed - as already explained.
     *
     */
    public static final class StuffDb
    {

        /*
            Create the STUFF table.
            An EffectOp is an operation which takes nothing, and returns nothing( fj.Unit ). It is useful for performing
            side effects which take no parameters, such as executing DDL:

         */
        public static DB<Unit> createStuffTable()
        {
            //this specific table has auto-generated ID
            return new EffectOp("CREATE TABLE STUFF (ID INTEGER PRIMARY KEY IDENTITY, DESCRIPTION NVARCHAR(200) NOT NULL)");
        }

        /*
            Insert a single record. Return the updateCount - in this case it will always be 1
         */
        public static DB<Integer> insertStuff(String description)
        {
            // an UpdateOp takes SQL, and a binder. It returns an update count
            // a binder is a piece of code which takes a prepared statement, and returns nothing.
            // it is so called because it binds the prepared statement parameters
            return new UpdateOp(
                    "INSERT INTO STUFF(DESCRIPTION) VALUES(?)",
                    // bind the single parameter of the statement
                    ps -> ps.setString(1, description)
            );
        }

        /*
            Insert a single record, and return its generated key
         */
        public static DB<Integer> insertStuffGetKey(String description)
        {
            /*
                An InsertGenKeysOp is the same as an UpdateOp, only that it expects an auto-generated key to be present
                after executing the update, and returns it as the result.

                InsertGenKeysOp.Int is a specialization which expects an Integer key
             */
            return new InsertGenKeysOp.Int(
                    "INSERT INTO STUFF(DESCRIPTION) VALUES(?)",
                    // you can see that the binder is the same as in the previous operation.
                    // in that case it is good practice to extract it as a static final field of your module
                    ps -> ps.setString(1, description)
            );
        }

        /*
            Select entries by description
         */
        public static DB<List<Stuff>> selectByDescription(String description)
        {
            /*
                A select operation takes SQL, a binder and a mapper, and returns an iterable of results
                A mapper is a function which maps a single row from the resultset to a single object
                A generic SelectOp also needs to be told what collection to use in the result
                The SelectOp.FjList specialization uses fj.data.List, which is an immutable singly-linked list.
                (Also known as Cons list)
                There is also a specialization for java Lists; or you can supply your own collection builder
             */
            return new SelectOp.FjList<>(
                    "SELECT ID, DESCRIPTION FROM STUFF WHERE DESCRIPTION=?",
                    ps -> ps.setString(1, description),
                    //build a Stuff from a resultset row
                    rs -> new Stuff(rs.getInt(1), rs.getString(2))
                    );
        }

        /*
            Select an entry by id
            The return type is fj.data.Option, since it may be that no entry with this id exists
            fj.data.Option is equivalent to Java Optional
         */
        public static DB<Option<Stuff>> selectByKey(int id)
        {

            //given a regular operation which returns an iterable:
            DB<List<Stuff>> temp = new SelectOp.FjList<>(
                    "SELECT ID, DESCRIPTION FROM STUFF WHERE ID=?",
                    ps -> ps.setInt(1, id),
                    rs -> new Stuff(rs.getInt(1), rs.getString(2)));

            // using the unique() function,
            // we can convert it to an operation which expects at most one result, and returns that optional result:
            return unique(temp);
        }

        /*
            Insert many entries, using addBatch / executeBatch, and return the update count.
            It is possible that a JDBC driver does not return the update count, therefore the optional result.
         */
        public static DB<Option<Integer>> insertMany(Iterable<String> descriptions)
        {
            return new BatchUpdateOp<>(
                    //SQL
                    "INSERT INTO STUFF(DESCRIPTION) VALUES(?)",
                    //given the current element of the iterable, return a binder which sets the parameters for that element
                    // if you extracted the binder from the previous operations in a field named SET_DESCRIPTION, code becomes
                    // description -> SET_DESCRIPTION
                    description -> ps -> ps.setString(1, description),
                    //the iterables to insert
                    descriptions
            );
        }

        /*
            Count all entries in the table, where the description is LIKE the passed parameter
         */
        public static DB<Long> count(String like)
        {
            String searchQuery = like.trim().toLowerCase();

            //an aggregate op expects the resultset to have one element, and that element to be cast to long
            //useful for numeric aggregate operations
            return new AggregateOp("SELECT COUNT(*) FROM STUFF WHERE DESCRIPTION LIKE LOWER(?)",
                    ps -> ps.setString(1, searchQuery + "%")
            );
        }


    }

    @Test
    public void testIt()
    {
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", ""
        ));
        dbi.submit(StuffDb.createStuffTable());

        Integer updateCount = dbi.submit(StuffDb.insertStuff("stuff 1"));
        assertThat(updateCount, is(1));

        Integer generatedKey = dbi.submit(StuffDb.insertStuffGetKey("stuff 2"));

        List<Stuff> stuffs = dbi.submit(StuffDb.selectByDescription("no such"));
        assertThat(stuffs.isEmpty(), is(true));

        stuffs = dbi.submit(StuffDb.selectByDescription("stuff 1"));
        //with head() we select the first element of the immutable list
        assertThat(stuffs.head().description, is("stuff 1"));
        //with tail() we select the rest of the list. it should be the empty list: nil()
        assertThat(stuffs.tail(), is(nil()));

        Option<Stuff> stuff2Option = dbi.submit(StuffDb.selectByKey(generatedKey));
        assertThat(stuff2Option.isSome(), is(true));
        Stuff stuff2 = stuff2Option.some();
        assertThat(stuff2, is(new Stuff(generatedKey, "stuff 2")));

        Option<Integer> batchCountOpt = dbi.submit(StuffDb.insertMany(asList("a", "b", "c")));
        Integer batchCount = batchCountOpt.some();
        assertThat(batchCount, is(3));

        Long stuCount = dbi.submit(StuffDb.count("StU"));
        //stuff 1 and stuff 2 match StU, so count should be 2
        assertThat(stuCount, is(2L));

    }

    private static class Stuff
    {
        public final int id;
        public final String description;

        public Stuff(int id, String description)
        {
            this.id = id;
            this.description = description;
        }

        @Override public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            Stuff stuff = (Stuff) o;

            if (id != stuff.id)
            {
                return false;
            }
            return description.equals(stuff.description);
        }

        @Override public int hashCode()
        {
            int result = id;
            result = 31 * result + description.hashCode();
            return result;
        }
    }
}