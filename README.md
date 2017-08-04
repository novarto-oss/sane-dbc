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
    
    
    // HikariCP support
    compile('com.novarto:sane-dbc-hikari:0.9-SNAPSHOT') {
        changing = true
    }
    
    // The core library provides support for asynchronous interpretation through AsyncDbInterpeter, which returns CompletableFuture
    // If in addition you require support for Guava ListenableFuture, use this jar:
    compile('com.novarto:sane-dbc-guava:0.9-SNAPSHOT') {
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

*By interpreting the `DB` result in our code using a `SyncDbInterpeter`, we explicitly state we do not not care about error handling, or about the caller thread getting blocked waiting on JDBC. This is mostly useful (and convenient) in unit testing your app's `DB` layer. Later we will learn about interpreters more suitable for production code.*

### Summary

`sane-dbc` works with only two abstractions

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
        dbi.submit(UserDB.insertUser("me@that.com", "abcd"));

        boolean success = dbi.submit(UserDB.login("me@that.com", "abcd"));
        assertThat(success, is(true));

        success = dbi.submit(UserDB.login("me@that.com", "wrong"));
        assertThat(success, is(false));

        success = dbi.submit(UserDB.login("larry@this.com", "abcd"));
        assertThat(success, is(false));
```

You can imagine many other examples. One might select a list of employees and group them by department, using
`java.util.stream.Collectors.groupingBy`. Or one might select a product catalog stored flat in a database, and turn it into
a `fj.data.Tree`. The important thing is, if you need to (and one always needs to) work with the data from your `DB`, you don't
run it - you just transform the original `DB` description using `map`. This way you delay performing side effects until the edge
of your app, where you interpret the `DB`.


#### `bind` (also known as `flatMap`)

What if we wanted to execute an operation, and upon its success, take the result and execute another operation based on that?

`bind` comes to the rescue:

```java
public final <B> DB<B> bind(final F<A, DB<B>> f);
```

So bind takes a `DB<A>` and a function which, given an A, produces a `DB<B>`. The final result is a `DB<B>`.
(If we had used `map` in this scenario, we would end up with a `DB<DB<B>>`, which is not convenient.)

For example, we might want to log a user in, and if successful, return their sales orders. If not, we return an error message.
(the full code is available [here](sane-dbc-examples/src/test/java/com/novarto/sanedbc/examples/BindExample.java))

```java
        //the operation authenticates the user, and reads their orders
        //it returns either an error message (string), in case the login fails; or the list of orders
        public static DB<Either<String, List<Order>>> authenticateAndGetOrders(String email, String pass)
        {
            // with bind (a.k.a. flatMap), we take the result of one operation, and use it to return another operation
            return UserDB.login(email, pass).bind(success -> {

                if(!success)
                {
                    Either<String, List<Order>> errorMessage = Either.left("auth failure");
                    // the DB.unit operation returns an immediate result with the passed value, without touching the connection
                    return DB.unit(errorMessage);
                }

                return selectOrdersByEmail(email).map(orders -> Either.right(orders));
            });
        }

        public static DB<List<Order>> selectOrdersByEmail(String userEmail)
        {
            return new SelectOp.FjList<>(
                    "SELECT ORDER_ID, USER_EMAIL, TEXT FROM ORDERS WHERE USER_EMAIL=?",
                    ps -> ps.setString(1, userEmail),
                    rs -> new Order(rs.getInt(1), rs.getString(2), rs.getString(3))
            );
        }
```

```java
        dbi.submit(UserDB.insertUser("john@doe.com", "abcd"));
        dbi.submit(UserDB.insertUser("foo@bar.com", "abcd"));

        dbi.submit(
                OrderDb.insertOrders(
                        arrayList(new CreateOrder("john@doe.com", "Hi there"),
                                new CreateOrder("foo@bar.com", "Bye there")
                        ))
        );

        Either<String, List<Order>> result = dbi.submit(OrderDb.authenticateAndGetOrders("john@doe.com", "abcd"));
        assertThat(result.isRight(), is(true));

        List<Order> orders = result.right().value();
        assertThat(orders.isSingle(), is(true));

        Order johnOrder = orders.head();
        assertThat(johnOrder.text, is("Hi there"));

        Either<String, List<Order>> shouldFail = dbi.submit(OrderDb.authenticateAndGetOrders("haxx0r", "abcd"));
        assertThat(shouldFail, is(Either.left("auth failure")));
```

With `bind` and `map` in our arsenal, we can compose and reuse existing `DB` operations,
as well as existing business logic functions (pure functions working on data and returning data, without being bothered with database specifics),
to arrive at composite operations that implement new use cases.

Because we only work with descriptions of DB interaction, and we only ever interpret those at the edge of our app,
there is no need to propagate `Connection` objects or `throws SqlException` clauses throughout the whole call stack,
as often happens when working with plain JDBC.

#### `fold` (also known as `reduce`)

Folding is the process of taking an iterable of things, and collapsing them to a single result.
Some examples of folds are:
- `java.util.stream.Stream.reduce()`
- `fj.data.List.foldLeft()`
- `fj.data.List.foldRight()`

One obvious way to fold a `ResultSet` into a single thing is to first get a `DB<Iterable<A>>` (for example, via a `SelectOp`)
and then do:
```java
db.map(iterable -> iterable.reduce(...));
``` 

This approach is fine in general; but it could be suboptimal if the `ResultSet` is very large, since we are constructing
an intermediate `Iterable` that we later discard.

We can do better: we can do the reduction directly while iterating the `ResultSet`, without using an intermediate collection:
(full source [here](sane-dbc-examples/src/test/java/com/novarto/sanedbc/examples/FoldExample.java))
```java
// we will select employees and group them by department
// the result type is immutable map from integer (department id) to a immutable list of employees from that department
FoldLeftSelectOp<HashArrayMappedTrie<Integer, List<Employee>>> selectGroupedByDepartment =
new FoldLeftSelectOp<>(
    "SELECT * FROM EMPLOYEES",
    NO_BINDER,
    (soFar, rs) -> {
        // the current row of the resultset, which we will append to the result so far
        Employee employee = new Employee(rs.getInt(1), rs.getString(2), rs.getInt(3));

        // the employees collected so far for this department
        // if this is the first employee of this department, fromThisDepartment will be none()
        Option<List<Employee>> fromThisDepartment = soFar.find(employee.departmentId);

        return fromThisDepartment
            //in case there are already employees collected for this department,
            //append this employee to the already collected (cons)
            //and update the map so far, associating departmentId with the appended list
            .map(employees -> soFar.set(employee.departmentId, employees.cons(employee)))
            //otherwise update the map so far, associating departmentId with a sized-one list
            //containing this employee
            .orSome(() -> soFar.set(employee.departmentId, List.single(employee)));
        },

        initial
    );
```

Perhaps that was a handful to grasp if you're not used to working with immutable collections. The point is, we are doing a 
reduction while directly iterating the `ResultSet`, with an empty initial value, without resorting to building an intermediate
collection from the `ResultSet`, such as `java.util.List` or `fj.data.List`.

The result of the DB operation will be just like a hashmap from integer to a linkedlist of integer, only difference being neither
the hashmap, nor the lists can be mutated.

In the advanced section of the tutorial we will discuss when and why it is good practice to return an immutable object from your `DB`.

#### `sequence`

Sometimes you end up with an iterable of `DB<A>`. It is convenient to treat that as a single DB<List<A>>, containing the
aggregated result. This is achieved via the `sequence` operator.

>> If you find yourself in need to use `sequence`, first investigate if you can change your SQL query so that it returns all
the results in a single `DB`, in one go. That will usually be more performant.

```java
import static com.novarto.sanedbc.core.ops.DbOps.sequence;
```

```java
//given an iterable of DB's
List<DB<String>> dbList = arrayList(
    DB.unit("foo"),
    DB.unit("bar"),
    DB.unit("baz")
);

//we can treat it as a DB<List>
DB<List<String>> db = sequence(dbList);

List<String> result = dbi.submit(db);

assertThat(result, is(arrayList("foo", "bar", "baz")));
```


### Interpreters

Since `DB` only **describes** database operations, a lot of aspects are left for interpretation-time. This includes

- JDBC connection management
- Transactional behaviour
- Error handling
- Forking execution in another thread

This is handy, since this way methods returning `DB<A>` are only concerned with selecting from / updating the database and
building / transforming / composing the result, and nothing else. It also means that the same `DB<A>` instance can be executed
with different interpreters, yielding a different behaviour.

Interpeters will generally support these two methods:

- `submit` will submit the operation for execution with `autoCommit=true`
- `transact` will submit the operation transactionally, i.e. with `autoCommit=false`, and the operation will be rolled back
upon error

Interpreters will require a piece of code at construction time, which is capable of returning JDBC connections. This is just like 
a `DataSource`, except you don't have to implement the `DataSource` interface.

#### `SyncDbInterpreter`

As we already know, the simplest interpreter is `SyncDbInterprer`. It blocks the caller thread, and tries to execute the operation.
Upon error it throws RuntimeException. Here it is in action, again:

```sql
"CREATE TABLE DUMMY (ID INTEGER PRIMARY KEY, X NVARCHAR(200)"
```

```java
// a datasource backed by a HikariCP connection pool
HikariDataSource hikariDS = Hikari.createHikari("jdbc:hsqldb:mem:test", "sa", "", new Properties());


SyncDbInterpreter sync = new SyncDbInterpreter(
    //transform a datasource to a Try0<Connection, SqlException>
    lift(hikariDS)
);

sync.submit(new EffectOp("INSERT INTO DUMMY VALUES(1,'a')"));
Long count = sync.submit(new AggregateOp("SELECT COUNT(*) FROM DUMMY"));
assertThat(count, is(1L));

try
{
    sync.transact(new EffectOp(
        "INSERT INTO DUMMY VALUES((2, 'b'), (1,'a'))"
    ));
    fail("expected constraint violation");
}
catch (RuntimeException e)
{
    assertThat(e.getCause(), is(instanceOf(SQLException.class)));
    count = sync.submit(new AggregateOp("SELECT COUNT(*) FROM DUMMY"));
    //transactional, no records were updated
    assertThat(count, is(1L));
}
```

Thus far we can see that
- We can supply interpreters with a data source; we can easily construct a production-suitable one, too;
- We can achieve transactional behaviour by calling `transact()` instead of `submit()` 

#### `ValidationDbInterpreter`
Next, we might want to take a more principled approach to error handling. A common technique is to embed the exception that `run`
can throw in the return type of our interpreter, thus treating errors as regular values, instead of relying on try / catch.

You can achieve this behaviour by utilising [ValidationDbInterpreter](sane-dbc-core/src/main/java/com/novarto/sanedbc/core/interpreter/ValidationDbInterpreter.java).
Its return type is `Validation<Exception, A>`. It is a box which either contains the failure - `java.lang.Exception`, or the
successful result `<A>`.

```java
//construct an interpreter that turns the result type to Validation<Exception, A>
// we can reuse the same data source across multiple interpreters
ValidationDbInterpreter vdb = new ValidationDbInterpreter(lift(hikariDS));

Validation<Exception, Long> successExpected = vdb.submit(new AggregateOp("SELECT COUNT(*) FROM DUMMY"));
assertThat(successExpected.isSuccess(), is(true));
assertThat(successExpected.success(), is(1L));

RuntimeException rte = new RuntimeException("failed I have");
Validation<Exception, Long> failExpected = vdb.submit(new DB<Long>()
{
    @Override public Long run(Connection c) throws SQLException
    {
        // all subclasses of java.lang.Exception and lifted to Validation, not just SqlException
        throw rte;
    }
});

assertThat(failExpected.isFail(), is(true));
assertThat(failExpected.fail(), is(rte));
```
#### `AsyncDbInterpreter`

The examples so far have the property of blocking the caller thread when we call `submit`/`transact`. For some use cases this might be 
unsuitable. For example, if we are in a HTTP worker thread, we don't want to hijack it while blocking on JDBC.

In such cases you can use an [AsyncDbInterpreter](sane-dbc-core/src/main/java/com/novarto/sanedbc/core/interpreter/AsyncDbInterpreter.java).
It returns a `CompletableFuture<A>` which will be completed exceptionally iff the underlying `DB` throws; otherwise it will be
completed successfully.

```java
ExecutorService ex = Executors.newCachedThreadPool();

// submits DB operations using the supplied executor, returns CompletableFuture<A>
AsyncDbInterpreter async = new AsyncDbInterpreter(lift(hikariDS), ex);

CompletableFuture<Long> countFuture = async.submit(new AggregateOp("SELECT COUNT(*) FROM DUMMY"));
//blocking call, don't do this in production
Long theCount = countFuture.get();
assertThat(theCount, is(1L));

CompletableFuture<Long> failedFuture = async.submit(new AggregateOp("BLA BLA BLA"));

//blocking call, don't do in production
TestUtil.waitFor(() -> failedFuture.isCompletedExceptionally(), 5, SECONDS);

Throwable failure = getFailure(failedFuture);

assertThat(failure.getCause(), instanceOf(SQLException.class));
```



## Advanced concepts

### Design guidelines

#### Threading semantics

#### Effective immutability

### Handling DDL

### Implementing your own interpreter


