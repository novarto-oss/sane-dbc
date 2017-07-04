package com.novarto.sanedbc.core.interpreter;

import fj.Try;
import fj.control.db.DB;
import fj.data.Validation;
import fj.function.Try0;

import java.sql.Connection;
import java.sql.SQLException;

import static com.novarto.sanedbc.core.interpreter.InterpreterUtils.transactional;

public class ValidationDbInterpreter
{
    private final Try0<Connection, SQLException> ds;

    public ValidationDbInterpreter(Try0<Connection, SQLException> ds)
    {
        this.ds = ds;
    }


    public <A> Validation<Exception, A> submit(DB<A> db)
    {
        return Try.<A, Exception>f(() -> {
            try (Connection c = ds.f())
            {
                return db.run(c);
            }
        }).f();

    }

    public <A> Validation<Exception, A> transact(DB<A> db)
    {

        return submit(transactional(db));
    }
}
