package fr.loghub.jdbc;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestInsertParser {

    @Test
    public void testSimpleInsert() {
        InsertParser parser = InsertParser.parse("INSERT INTO mytable (col1, col2) VALUES (?, ?)");
        assertEquals("mytable", parser.tableName());
        assertEquals(List.of("col1", "col2"), parser.columns());
        assertEquals(2, parser.parametersCount());
    }

    @Test
    public void testInsertNoColumns() {
        InsertParser parser = InsertParser.parse("INSERT INTO mytable VALUES (?, ?, ?)");
        assertEquals("mytable", parser.tableName());
        assertTrue(parser.columns().isEmpty());
        assertEquals(3, parser.parametersCount());
    }

    @Test
    public void testCaseInsensitive() {
        InsertParser parser = InsertParser.parse("insert into MYTABLE (A) values (?)");
        assertEquals("MYTABLE", parser.tableName());
        assertEquals(List.of("A"), parser.columns());
        assertEquals(1, parser.parametersCount());
    }

    @Test
    public void testInvalidInsert() {
        assertThrows(IllegalArgumentException.class, () -> {
            InsertParser.parse("SELECT * FROM mytable");
        });
    }
}
