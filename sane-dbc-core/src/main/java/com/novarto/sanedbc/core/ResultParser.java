package com.novarto.sanedbc.core;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by fmap on 18.08.16.
 */
public class ResultParser
{
    public static List<LinkedHashMap<String, Object>> parseResultSet(ResultSet s)
    {

        List<LinkedHashMap<String, Object>> result = new ArrayList<>();

        try
        {
            ResultSetMetaData metaData = s.getMetaData();

            int totalColumns = metaData.getColumnCount();

            while (s.next())
            {

                LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < totalColumns; i++)
                {
                    row.put(metaData.getColumnName(i + 1), s.getObject(i + 1));
                }

                result.add(row);

            }

            return result;

        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }

    }

    public static List<LinkedHashMap<String, Object>> select(String sql, Connection c)
    {
        try (PreparedStatement ps = c.prepareStatement(sql))
        {
            ResultSet rs = ps.executeQuery();
            return parseResultSet(rs);
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }
}
