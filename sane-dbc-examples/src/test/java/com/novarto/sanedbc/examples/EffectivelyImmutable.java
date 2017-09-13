package com.novarto.sanedbc.examples;

import com.novarto.lang.Collections;
import com.novarto.sanedbc.core.interpreter.SyncDbInterpreter;
import fj.P2;
import fj.control.db.DB;
import fj.data.Tree;
import org.junit.Test;

import java.sql.DriverManager;
import java.util.List;
import java.util.Map;

import static fj.P.p;
import static fj.data.List.arrayList;
import static fj.data.Tree.leaf;
import static fj.data.Tree.node;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class EffectivelyImmutable
{

    private DB<Tree<Integer>> intTree()
    {
        /*
            our source data represents rows of parent-child relationships,
            i.e. the following tree:

            1
                2
                3
                    4
                    5

            the DB returns a mutable data structure - java.util.ArrayList of the parent-child rows

         */
        DB<List<P2<Integer, Integer>>> raw = DB.unit(
                asList(
                        p(1, 2),
                        p(1, 3),
                        p(3, 4),
                        p(3, 5)
                )
        );

        //next we convert it to another mutable data structure, a Map holding parents as keys, and lists of children as values
        DB<Map<Integer, List<Integer>>> asMap = raw.map(xs -> Collections.toMap(xs, x -> x._1(), x -> x._2()));

        //finally we convert the map to an immutable tree structure
        DB<Tree<Integer>> result = asMap.map(x -> toTree(1, x));

        //even though, internally, we used mutable data structures while building the pipeline,
        //the final result is an immutable data structure.
        //the intermediate representation was an implementation detail, which is not observable outside this method.
        //this technique is dubbed "Effectively immutable"

        return result;

    }

    private <A> Tree<A> toTree(A root, Map<A, ? extends Iterable<A>> map)
    {
        Iterable<A> theseChildren = map.get(root);
        if (theseChildren == null || Collections.isEmpty(theseChildren))
        {
            return leaf(root);
        }

        fj.data.List.Buffer<Tree<A>> childNodes = fj.data.List.Buffer.empty();
        for (A child : theseChildren)
        {
            childNodes = childNodes.snoc(toTree(child, map));
        }

        return node(root, childNodes.toList());
    }


    @Test
    public void testIt()
    {

        SyncDbInterpreter dbi = new SyncDbInterpreter(
                () -> DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "")

        );

        Tree<Integer> result = dbi.submit(intTree());

        Tree<Integer> expected = node(
                1,
                arrayList(
                        leaf(2),
                        node(
                                3,
                                arrayList(leaf(4), leaf(5))
                        )
                )
        );

        assertThat(
                result,
                is(expected)
        );

    }
}
