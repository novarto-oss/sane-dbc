package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.BatchInsertGenKeysOp;
import com.novarto.sanedbc.core.ops.EffectOp;
import com.novarto.sanedbc.core.ops.SelectOp;
import fj.Unit;
import fj.control.db.DB;
import fj.data.Either;
import fj.data.List;
import org.junit.Test;

import java.sql.DriverManager;
import com.novarto.sanedbc.examples.MapExample1.UserDB;

import static fj.data.List.arrayList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class BindExample
{

    public static final class OrderDb
    {
        public static final DB<Unit> CREATE_TABLE = new EffectOp(
                "CREATE TABLE ORDERS ( USER_EMAIL VARCHAR(200) NOT NULL, ORDER_ID INTEGER PRIMARY KEY IDENTITY," +
                        "TEXT NVARCHAR(500), FOREIGN KEY (USER_EMAIL) REFERENCES USERS(EMAIL) )"
        );

        public static DB<Unit> insertOrders(List<CreateOrder> newOrders)
        {
            // insert all the orders via batch, return the ORDER_ID keys
            DB<List<Integer>> insertOrdersGetKeys = new BatchInsertGenKeysOp.FjList<>(
                    "INSERT INTO ORDERS(USER_EMAIL, TEXT) VALUES(?, ?)",
                    x -> ps -> {
                        ps.setString(1, x.userEmail);
                        ps.setString(2, x.text);
                    },
                    newOrders,
                    rs -> rs.getInt(1)
            );

            return insertOrdersGetKeys.map(ignore -> Unit.unit());
        }

        //the operation authenticates the user, and reads their orders
        //it returns either an error message (string), in case the login fails; or the list of orders
        public static DB<Either<String, List<Order>>> authenticateAndGetOrders(String email, String pass)
        {
            // with bind we take the result of one operation, and use it to return another operation
            return UserDB.login(email, pass).bind(success -> {

                if (!success)
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

    }

    @Test
    public void testIt()
    {

        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:bind_example", "sa", "")
        );

        dbi.submit(UserDB.CREATE_USER_TABLE);
        dbi.submit(OrderDb.CREATE_TABLE);


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
    }

    public static class Order
    {
        public final int id;
        public final String userEmail;
        public final String text;

        public Order(int id, String userEmail, String text)
        {
            this.id = id;
            this.userEmail = userEmail;
            this.text = text;
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

            Order order = (Order) o;

            return id == order.id;
        }

        @Override public int hashCode()
        {
            return id;
        }
    }

    public static class CreateOrder
    {
        public final String userEmail;
        public final String text;

        public CreateOrder(String userEmail, String text)
        {
            this.userEmail = userEmail;
            this.text = text;
        }
    }
}
