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

