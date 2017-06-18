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
batch Insert / Update, Aggregate (count, etc), and so forth. It's best to see them in action, so take a look at our
[Basic usage example](sane-dbc-examples/src/test/java/com/novarto/sanedbc/examples/BasicUsage.java)


### Composition

### Transactional interpretation

### Asynchronous interpretation

## Advanced concepts

### Design guidelines

### Handling DDL


