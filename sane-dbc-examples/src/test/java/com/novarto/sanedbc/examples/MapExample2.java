package com.novarto.sanedbc.examples;

import com.novarto.sanedbc.core.SqlStringUtils;
import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.BatchUpdateOp;
import com.novarto.sanedbc.core.ops.EffectOp;
import com.novarto.sanedbc.core.ops.SelectOp;
import fj.Unit;
import fj.control.db.DB;
import org.junit.After;
import org.junit.Test;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.novarto.lang.Collections.size;
import static com.novarto.sanedbc.core.ops.Binders.iterableBinder;
import static java.text.MessageFormat.format;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class MapExample2
{
    @Test
    public void groupBy()
    {
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")
        );

        dbi.submit(new EffectOp("CREATE TABLE EMPLOYEES (ID INTEGER PRIMARY KEY, NAME VARCHAR(300), DEPARTMENT_ID INTEGER)"));


        dbi.submit(
                EmployeeDb.insert(asList(
                        new Employee(1, "Ivan", 1),
                        new Employee(2, "Peter", 1),
                        new Employee(3, "Stephan", 3),
                        new Employee(4, "George", 3),
                        new Employee(5, "Juergen", 2)
                )));

        DB<List<Employee>> selectEmployees = EmployeeDb.selectByIds(asList(2, 1, 4, 3));

        DB<Map<Integer, List<Employee>>> selectEmployeesById = selectEmployees
                .map(employees -> employees.stream().collect(Collectors.groupingBy(employee -> employee.departmentId)));


        Map<Integer, List<Employee>> expected = new HashMap<>();

        expected.put(1,
                asList(new Employee(1, "Ivan", 1), new Employee(2, "Peter", 1)));

        expected.put(3,
                asList(new Employee(3, "Stephan", 3), new Employee(4, "George", 3)));

        assertThat(
                dbi.submit(selectEmployeesById),
                is(expected)
        );

    }

    public static class Employee
    {
        public final int id;
        public final String name;
        public final int departmentId;

        public Employee(int id, String name, int departmentId)
        {
            this.id = id;
            this.name = name;
            this.departmentId = departmentId;
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

            Employee employee = (Employee) o;

            return id == employee.id;
        }

        @Override public int hashCode()
        {
            return id;
        }

        @Override public String toString()
        {
            return "Employee{" + "id=" + id + ", name='" + name + '\'' + ", departmentId=" + departmentId + '}';
        }
    }

    @After
    public void tearDown()
    {
        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")
        );
        dbi.submit(new EffectOp("DROP TABLE EMPLOYEES"));

    }



    public static class EmployeeDb
    {

        public static DB<Unit> insert(Iterable<Employee> xs)
        {
            return new BatchUpdateOp<>("INSERT INTO EMPLOYEES (ID, NAME, DEPARTMENT_ID) VALUES (?, ?, ?)", x -> ps -> {
                ps.setInt(1, x.id);
                ps.setString(2, x.name);
                ps.setInt(3, x.departmentId);
            }, xs)
                    .map(ignore -> Unit.unit());
        }

        public static DB<List<Employee>> selectByIds(Iterable<Integer> ids)
        {
            return new SelectOp.List<>(
                    format("SELECT ID, NAME, DEPARTMENT_ID FROM EMPLOYEES WHERE ID IN ({0}) ORDER BY ID",
                            SqlStringUtils.placeholders(size(ids))
                    ),
                    iterableBinder((idx, ps, id) -> {
                        ps.setInt(idx++, id);
                        return idx;
                    }, ids),
                    rs -> new Employee(rs.getInt(1), rs.getString(2), rs.getInt(3))
            );
        }


    }


}
