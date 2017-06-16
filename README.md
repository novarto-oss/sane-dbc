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
* the DB class to compose operations into larger operations and programs, and to separate DB operations' **definition** from
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

For operations returning nothing (DB mutations, where we are only interested in changing the tables state), in Java it is
customary to use `void`, as above. However, we want to parametrize on the return type, and `java.lang.Void` is strange in that its
only 'valid' value is `null`, which can lead to NPE. We will instead use another type with only one value which is null-safe -
[Unit](https://github.com/functionaljava/functionaljava/blob/master/core/src/main/java/fj/Unit.java). You obtain a Unit value
to return by calling `Unit.unit()`.


 


