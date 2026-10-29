package com.distributed_ide.javafx_client.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Un diagnostic LSP (erreur / avertissement) pour affichage dans la vue diagnostics.
 */
public class DiagnosticItem {

    public enum Severity { ERROR, WARNING, INFORMATION, HINT }

    private final StringProperty uriProperty = new SimpleStringProperty("");
    private final IntegerProperty lineProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty columnProperty = new SimpleIntegerProperty(0);
    private final StringProperty messageProperty = new SimpleStringProperty("");
    private final StringProperty severityProperty = new SimpleStringProperty("ERROR");

    public DiagnosticItem() {}

    public DiagnosticItem(String uri, int line, int column, String message, Severity severity) {
        uriProperty.set(uri != null ? uri : "");
        lineProperty.set(line);
        columnProperty.set(column);
        messageProperty.set(message != null ? message : "");
        severityProperty.set(severity != null ? severity.name() : "ERROR");
    }

    public String getUri() { return uriProperty.get(); }
    public void setUri(String uri) { uriProperty.set(uri); }
    public StringProperty uriProperty() { return uriProperty; }

    public int getLine() { return lineProperty.get(); }
    public void setLine(int line) { lineProperty.set(line); }
    public IntegerProperty lineProperty() { return lineProperty; }

    public int getColumn() { return columnProperty.get(); }
    public void setColumn(int col) { columnProperty.set(col); }
    public IntegerProperty columnProperty() { return columnProperty; }

    public String getMessage() { return messageProperty.get(); }
    public void setMessage(String msg) { messageProperty.set(msg); }
    public StringProperty messageProperty() { return messageProperty; }

    public String getSeverity() { return severityProperty.get(); }
    public void setSeverity(String s) { severityProperty.set(s); }
    public StringProperty severityProperty() { return severityProperty; }

    public Severity getSeverityEnum() {
        try {
            return Severity.valueOf(getSeverity());
        } catch (Exception e) {
            return Severity.ERROR;
        }
    }
}
