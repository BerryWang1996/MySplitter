package com.mysplitter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultReadAndWriteParserTest {

    private final DefaultReadAndWriteParser parser = new DefaultReadAndWriteParser();

    @Test
    public void shouldRoutePlainSelectToReaders() {
        assertEquals("readers", parser.parseOperation("SELECT * FROM user WHERE id = 1"));
        assertEquals("readers", parser.parseOperation("  select 1  "));
    }

    @Test
    public void shouldIgnoreLeadingPlainCommentsForSelects() {
        assertEquals("readers", parser.parseOperation("-- leading comment\nSELECT * FROM user"));
        assertEquals("readers", parser.parseOperation("# leading comment\nSELECT * FROM user"));
        assertEquals("readers", parser.parseOperation("/* leading comment */ SELECT * FROM user"));
    }

    @Test
    public void shouldRouteLockSensitiveSelectsToWriters() {
        assertEquals("writers", parser.parseOperation("SELECT * FROM user WHERE id = 1 FOR UPDATE"));
        assertEquals("writers", parser.parseOperation("SELECT * FROM user WHERE id = 1 FOR SHARE"));
        assertEquals("writers", parser.parseOperation("SELECT * FROM user LOCK IN SHARE MODE"));
    }

    @Test
    public void shouldRouteAmbiguousStatementsToWriters() {
        assertEquals("writers", parser.parseOperation("WITH recent AS (SELECT * FROM user) SELECT * FROM recent"));
        assertEquals("writers", parser.parseOperation("SHOW TABLES"));
        assertEquals("writers", parser.parseOperation("EXPLAIN SELECT * FROM user"));
        assertEquals("writers", parser.parseOperation("CALL rebuild_user_stats()"));
    }

    @Test
    public void shouldRouteVendorHintsAndSideEffectSelectsToWriters() {
        assertEquals("writers", parser.parseOperation("/*+ MAX_EXECUTION_TIME(1000) */ SELECT * FROM user"));
        assertEquals("writers", parser.parseOperation("SELECT /*+ MAX_EXECUTION_TIME(1000) */ * FROM user"));
        assertEquals("writers", parser.parseOperation("/*!40101 SELECT * FROM user */"));
        assertEquals("writers", parser.parseOperation("SELECT GET_LOCK('user-lock', 1)"));
    }

    @Test
    public void shouldRouteBlankOrNullSqlToWriters() {
        assertEquals("writers", parser.parseOperation(null));
        assertEquals("writers", parser.parseOperation("   "));
        assertEquals("writers", parser.parseOperation("/* unfinished"));
    }
}
