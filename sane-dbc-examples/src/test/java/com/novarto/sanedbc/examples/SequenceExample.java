package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import fj.control.db.DB;
import fj.data.List;
import org.junit.Test;

import java.sql.DriverManager;

import static com.novarto.sanedbc.core.ops.DbOps.sequence;
import static fj.data.List.arrayList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class SequenceExample
{
    @Test
    public void testIt()
    {
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")
        );

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


    }
}
