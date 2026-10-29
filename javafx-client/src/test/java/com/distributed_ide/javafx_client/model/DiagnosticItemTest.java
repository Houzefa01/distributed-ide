package com.distributed_ide.javafx_client.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticItemTest {

    @Test
    void constructor_setsProperties() {
        DiagnosticItem d = new DiagnosticItem("file:///a.java", 5, 2, "undefined variable", DiagnosticItem.Severity.ERROR);
        assertEquals("file:///a.java", d.getUri());
        assertEquals(5, d.getLine());
        assertEquals(2, d.getColumn());
        assertEquals("undefined variable", d.getMessage());
        assertEquals("ERROR", d.getSeverity());
        assertEquals(DiagnosticItem.Severity.ERROR, d.getSeverityEnum());
    }

    @Test
    void setters_updateProperties() {
        DiagnosticItem d = new DiagnosticItem("u", 0, 0, "m", DiagnosticItem.Severity.WARNING);
        d.setUri("uri2");
        d.setLine(10);
        d.setColumn(3);
        d.setMessage("msg2");
        d.setSeverity("INFORMATION");
        assertEquals("uri2", d.getUri());
        assertEquals(10, d.getLine());
        assertEquals(3, d.getColumn());
        assertEquals("msg2", d.getMessage());
        assertEquals(DiagnosticItem.Severity.INFORMATION, d.getSeverityEnum());
    }
}
