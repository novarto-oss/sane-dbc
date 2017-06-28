package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.EffectOp;
import com.novarto.sanedbc.core.ops.SelectOp;
import com.novarto.sanedbc.core.ops.UpdateOp;
import fj.Unit;
import fj.control.db.DB;
import fj.data.Option;
import org.junit.Test;

import java.sql.DriverManager;

import static com.novarto.sanedbc.core.ops.DbOps.unique;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class MapExample1
{

    public static class User
    {
        public final String user;
        public final String hash;

        public User(String user, String hash)
        {
            this.user = user;
            this.hash = hash;
        }
    }

    public static class UserDB
    {
        public static final DB<Unit> CREATE_USER_TABLE = new EffectOp(
                "CREATE TABLE USERS (EMAIL VARCHAR(200) PRIMARY KEY, PASSWORD_HASH VARCHAR(200) NOT NULL)"
        );

        public static DB<Unit> insertUser(String email, String pass)
        {
            return new UpdateOp(
                    "INSERT INTO USERS(EMAIL, PASSWORD_HASH) VALUES(?, ?)",
                    ps -> {
                        ps.setString(1, email);
                        ps.setString(2, hash(pass));
                    }
            ).map(ignore -> Unit.unit());
        }

        private static String hash(String password)
        {
            //please don't do this in production
            return new StringBuilder(password).reverse().toString();
        }

        private static DB<Option<User>> selectUser(String email)
        {
            return unique(new SelectOp.FjList<>(
                    "SELECT * FROM USERS WHERE EMAIL=?",
                    ps -> ps.setString(1, email),
                    rs -> new User(rs.getString(1), rs.getString(2))
            ));
        }

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
                return loginOk(user, pass);
            });
        }

        private static boolean loginOk(User user, String password)
        {
            return hash(password).equals(user.hash);
        }
    }



    @Test
    public void testIt()
    {
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")
        );

        dbi.submit(UserDB.CREATE_USER_TABLE);
        dbi.submit(UserDB.insertUser("me@that.com", "abcd"));

        boolean success = dbi.submit(UserDB.login("me@that.com", "abcd"));
        assertThat(success, is(true));

        success = dbi.submit(UserDB.login("me@that.com", "wrong"));
        assertThat(success, is(false));

        success = dbi.submit(UserDB.login("larry@this.com", "abcd"));
        assertThat(success, is(false));
    }
}
