package com.novarto.sanedbc.examples;

import com.novarto.lang.Collections;
import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import com.novarto.sanedbc.core.ops.EffectOp;
import com.novarto.sanedbc.core.ops.FoldLeftSelectOp;
import com.novarto.sanedbc.examples.MapExample2.Employee;
import com.novarto.sanedbc.examples.MapExample2.EmployeeDb;
import fj.Equal;
import fj.Hash;
import fj.data.List;
import fj.data.Option;
import fj.data.hamt.HashArrayMappedTrie;
import org.junit.After;
import org.junit.Test;

import java.sql.DriverManager;
import java.util.HashSet;

import static com.novarto.sanedbc.core.ops.Binders.NO_BINDER;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FoldExample
{

    private final SyncDbInterpreter dbi = new SyncDbInterpreter(
            () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")
    );

    @Test
    public void testIt()
    {

        dbi.submit(new EffectOp("CREATE TABLE EMPLOYEES (ID INTEGER PRIMARY KEY, NAME VARCHAR(300), DEPARTMENT_ID INTEGER)"));

        dbi.submit(EmployeeDb.insert(asList(
                new Employee(1, "Mike", 1),
                new Employee(2, "Nike", 1),
                new Employee(3, "Bike", 2),
                new Employee(4, "Rike", 2),
                new Employee(5, "Fike", 3)
        )));



        // the fold operation takes a 'zero' parameter - it is the initial value of the fold (i.e. reduction)
        HashArrayMappedTrie<Integer, List<Employee>> initial = HashArrayMappedTrie.empty(Equal.anyEqual(), Hash.anyHash());

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

        HashArrayMappedTrie<Integer, List<Employee>> result = dbi.submit(selectGroupedByDepartment);

        assertThat(result.length(), is(3));

        assertThat(new HashSet<>(result.find(1).some().toCollection()),
                is(Collections.javaSet(new Employee(1, "Mike", 1), new Employee(2, "Nike", 1))));

        assertThat(new HashSet<>(result.find(2).some().toCollection()),
                is(Collections.javaSet(new Employee(3, "Bike", 2), new Employee(4, "Rike", 2))));

        assertThat(new HashSet<>(result.find(3).some().toCollection()),
                is(Collections.javaSet(new Employee(5, "Fike", 3))));
    }

    @After
    public void teardown()
    {
        dbi.submit(new EffectOp("DROP TABLE EMPLOYEES"));
    }
}
