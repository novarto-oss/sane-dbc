# sane-dbc
A sane approach to interacting with an RDBMS in Java

# Intro
`sane-dbc` addresses the aspect of RDBMS interaction in the Java programming language.

While numerous libraries exist in the JVM ecosystem which cope with this in an elegant, well-grounded and efficient manner,
to our knowledge the **Java** alternatives currently widespread fall short in one way or another.

`sane-dbc` provides an effective, performant and robust way to interact with RDBMS in Java. It is based on SQL and the
[DB Monad](https://github.com/functionaljava/functionaljava/blob/master/core/src/main/java/fj/control/db/DB.java).

That is, you use
* SQL and your RDBMS system's specific facilities to interact with it in the way best suited to your application
* the DB class to compose operations into larger operations and programs, and to separate DB operations' **description** from
their execution (**interpretation**), which leads to large increase in programmer efficiency and code correctness
* the facilities provided by `sane-dbc` to achieve common programming tasks, as well as spare yourself from boilerplate, such as the one
inherent in JDBC

# Quickstart

`sane-dbc` is hosted on [Artifactory OSS](https://oss.jfrog.org/artifactory/libs-snapshot/) repository.

With `gradle`, you can refer to `sane-dbc` snapshot version in the following way:

```groovy
repositories {
    maven {
        url 'https://oss.jfrog.org/artifactory/libs-snapshot/'
    }
}

dependencies {

    // the sane-dbc library
    compile('com.novarto:sane-dbc-core:0.9-SNAPSHOT') {
        changing = true
    }
    
    // optionally, an asynchronous DB interpreter which utilizes HikariCP as the connection pool implementation and 
    // ListenableFuture as the result type
    compile('com.novarto:sane-dbc-hikari:0.9-SNAPSHOT') {
        changing = true
    }
}
```
You can refer to `sane-dbc` in Maven and other tools equivalently.

Release versions will be published once there is interest in the library.

# Tutorial

## Concepts

### The essence of a JDBC interaction

Let's start with a small example. We need a table to work with:
```hsqldb
CREATE TABLE FOO (ID INTEGER IDENTITY PRIMARY KEY, DESCRIPTION NVARCHAR(100))
```
Let's look at some plain old pieces of code which read or mutate it:

```java
    void insertSomeFoos(Connection c) throws SQLException
    {
        try(PreparedStatement insert = c.prepareStatement(
                "INSERT INTO FOO(DESCRIPTION) VALUES (?)"
        ))
        {
            for(String descr: asList("one", "two", "three"))
            {
                insert.setString(1, descr);
                insert.executeUpdate();
            }

        }
    }
```

```java
    List<Foo> selectTheFoos(Connection c) throws SQLException
    {
        try(PreparedStatement s = c.prepareStatement("SELECT ID, DESCRIPTION FROM FOO"))
        {
            List<Foo> result = new ArrayList<>();
            ResultSet rs = s.executeQuery();
            while(rs.next())
            {
                result.add(new Foo(rs.getInt(1), rs.getString(2)));
            }

            return result;

        }
    }
```

An RDBMS interaction in Java, then, takes the form
```java
A run(Connection c) throws SQLException;
```
, where `A` is a type parameter.
This is exactly the `run` method of the [DB class](https://github.com/functionaljava/functionaljava/blob/master/core/src/main/java/fj/control/db/DB.java).
We'll be using that from now on.

*For operations returning nothing (DB mutations, where we are only interested in changing the tables state), in Java it is
customary to use `void`, as above. However, we want to parametrize on the return type, and `java.lang.Void` is strange in that its
only 'valid' value is `null`, which can lead to NPE. We will instead use another type with only one value which is null-safe -
[Unit](https://github.com/functionaljava/functionaljava/blob/master/core/src/main/java/fj/Unit.java). You obtain a Unit value
to return by calling `Unit.unit()`.*

### Description
>A `DB` instance is just a **description** of a database interaction.

If we just construct a `DB`, nothing will happen - there will
be no connections, calling the database, or any other *side effects*. In other words, a method returning a `DB` is
**[referentially transparent](https://en.wikipedia.org/wiki/Referential_transparency)** 

(*all the example code in this tutorial is published in our [repo](sane-dbc-examples/src/test/java/com/novarto/sanedbc/examples)*)

 ```java
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
```

### Interpreter
In the above example nothing happens, and rightly so. Nobody called the `run()` method on our `DB` (in this case, `SelectOp`).

>A piece of code which takes a `DB<A>`, runs it, and returns some other value, is called an **interpreter**

Since running a `DB` requires a `Connection`, the interpreter also needs to know how to spawn connections.

The simplest interpreter we can imagine is one that calls run() in the caller thread, and upon success returns the `DB` result.
Upon error it throws.

We have such an interpreter baked into `sane-dbc-core`, we just need to supply it with the code which spawns connections,
and give it a spin:


```java

            @Test
            public void syncInterpreter()
            {
                // create a synchronous DB interpreter. It is a stateless object, and the act of creating one is also
                // referentially transparent
                SyncDbInterpreter dbi = new SyncDbInterpreter(
                        // provide a piece of code which knows how to spawn connections
                        // in this case we are just using the DriverManager
                        () -> DriverManager.getConnection("jdbc:hsqldb:mem:DescribeVsInterpret", "sa", "")
                );
        
                // submit an Update (mutate) operation which creates a table
                dbi.submit(new UpdateOp(
                        "CREATE TABLE FOO (WHATEVER VARCHAR(200))",
                        NO_BINDER
                ));
        
                // objects we will insert in the table
                List<String> helloSaneDbc = List.arrayList("hello", "sane", "dbc");
        
                //insert some data
                Option<Integer> updateCount = dbi.submit(
                        //this describes an operation which inserts an iterable of objects in a table via addBatch / executeBatch
                        new BatchUpdateOp<>(
                                "INSERT INTO FOO(WHATEVER) VALUES(?)",
                                x -> preparedStatement -> preparedStatement.setString(1, x),
                                helloSaneDbc
                        )
                );
        
                // the operation returns an optional update count, since the JDBC driver might not return an update count at all
                assertThat(updateCount, is(some(3)));
        
                
                List<String> result = dbi.submit(
                        // select all of the objects in the table
                        new SelectOp.FjList<>("SELECT WHATEVER FROM FOO", NO_BINDER, resultSet -> resultSet.getString(1))
                );
        
                
                assertThat(result, is(helloSaneDbc));
            }

``` 

### Summary
To sum up, `sane-dbc` works with only two abstractions

* A `DB` is a description of operations which will be executed against a database
* An interpreter is responsible for executing the operations and returning the results. It is also responsible for all the specifics
of execution, such as connection management, threading, error handling, transactions, etc.

The way it usually works is that you only call the interpreter at the **edge** of your application - the `main` method;
a webservice method; a testcase. In this way, the bulk of your program only works with `DB` descriptions, and is referentially transparent.
Side effects are only performed in specific places, and in a controlled manner.

These are all the concepts you need to know to start working with `sane-dbc`. Let's now jump into details.

## Usage

### Basic usage examples

One of the things `sane-dbc` does is provide ready-to-use `DB` implementations for common tasks such as Select, Insert / Update,
batch Insert / Update, Aggregate (count, etc), and so forth.

Let's have a look at some of those in an example:
```java
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

    public static class Stuff
    {
        public final int id;
        public final String description;

        public Stuff(int id, String description)
        {
            this.id = id;
            this.description = description;
        }

        @Override public boolean equals(Object o); //... noise ommitted
        

        @Override public int hashCode(); //... noise ommitted
       
    }
}
```

There's a couple more [built in operations](sane-dbc-core/src/main/java/com/novarto/sanedbc/core/ops) you can use, possibly
with more coming in the future. Consult the javadoc of the individual operations for more details.

Hopefully we have demonstrated scrapping the JDBC boilerplate into reusable operations makes Java SQL programming reasonable.
You can further apply this principle to your program by implementing your own `DB` instances.

A good property of the `DB` abstraction is that it is practically zero-cost. There is no SQL generation, automatic object mapping 
or anything of that sort going on. The total runtime overhead of using sane-dbc is the creation of a constant number of objects
(usually a couple) per DB operation. This gets amortized by the network call, and even by the creation of your DB's result object.

Another very useful property of the approach is that you are in full control of database interaction. Creating the SQL; supplying the
input; choosing the exact result type - these are all done by the library user.


### Composition

The `DB` abstraction enables us to compose `DB` instances with existing functions, and `DB` instances with other `DB` instances,
to obtain composite `DB`'s as a result.

#### `map`

The `DB` class defines the following operation:

```java
public final <B> DB<B> map(final F<A, B> f)
```
So map takes a `DB<A>`, a function `f: A -> B` and returns a `DB<B>`. That happens if the original DB<A> is successful.
If not, the `DB<B>` will just contain the original error. By contain, we mean it will throw it upon `run()`.

Let's see `map()` in action by implementing a simple login service (complete source [here](sane-dbc-examples/src/test/java/com/novarto/sanedbc/examples/MapExample1.java)):

```java
public static class UserDB
    {


        public static DB<Boolean> login(String email, String pass)
        {
            //we map an operation DB<Option<User>> with a function which takes an Option<User> and returns a boolean,
            //resulting in a DB<Boolean>
            return selectUser(email).map(userOpt -> {
                if (userOpt.isNone())
                {
                    //invalid email
                    return false;
                }

                User user = userOpt.some();
                //check pass hash matches
                return loginOk(user, pass);
            });
        }

        private static boolean loginOk(User user, String password)
        {
            return hash(password).equals(user.hash);
        }
        
        private static String hash(String password); // impl omitted

    }
```

```java
        dbi.submit(UserDB.CREATE_USER_TABLE);
        dbi.submit(UserDB.insertUser("me@that.com", "abcd"));

        boolean success = dbi.submit(UserDB.login("me@that.com", "abcd"));
        assertThat(success, is(true));

        success = dbi.submit(UserDB.login("me@that.com", "wrong"));
        assertThat(success, is(false));

        success = dbi.submit(UserDB.login("larry@this.com", "abcd"));
        assertThat(success, is(false));
```



### Transactional interpretation

### Asynchronous interpretation

## Advanced concepts

### Design guidelines

### Handling DDL


