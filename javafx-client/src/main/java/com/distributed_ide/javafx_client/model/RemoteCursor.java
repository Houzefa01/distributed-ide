package com.distributed_ide.javafx_client.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Représente un collaborateur distant : curseur, sélection, identité.
 * Utilisé pour le rendu multi-utilisateurs dans l'éditeur.
 */
public class RemoteCursor {

    private final StringProperty clientIdProperty = new SimpleStringProperty("");
    private final StringProperty nodeIdProperty = new SimpleStringProperty("");
    private final StringProperty usernameProperty = new SimpleStringProperty("");
    private final StringProperty colorProperty = new SimpleStringProperty("#00bcd4");
    private final StringProperty documentIdProperty = new SimpleStringProperty("");
    private final IntegerProperty lineProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty columnProperty = new SimpleIntegerProperty(0);

    // Sélection distante
    private final IntegerProperty selStartLineProperty = new SimpleIntegerProperty(-1);
    private final IntegerProperty selStartColProperty = new SimpleIntegerProperty(-1);
    private final IntegerProperty selEndLineProperty = new SimpleIntegerProperty(-1);
    private final IntegerProperty selEndColProperty = new SimpleIntegerProperty(-1);

    public RemoteCursor() {}

    public RemoteCursor(String clientId, String nodeId) {
        if (clientId != null) clientIdProperty.set(clientId);
        if (nodeId != null) nodeIdProperty.set(nodeId);
    }

    public RemoteCursor(String clientId, String username, String color) {
        if (clientId != null) clientIdProperty.set(clientId);
        if (username != null) usernameProperty.set(username);
        if (color != null) colorProperty.set(color);
    }

    public String getClientId() { return clientIdProperty.get(); }
    public void setClientId(String id) { clientIdProperty.set(id); }
    public StringProperty clientIdProperty() { return clientIdProperty; }

    public String getNodeId() { return nodeIdProperty.get(); }
    public void setNodeId(String id) { nodeIdProperty.set(id); }
    public StringProperty nodeIdProperty() { return nodeIdProperty; }

    public String getUsername() { return usernameProperty.get(); }
    public void setUsername(String u) { usernameProperty.set(u != null ? u : ""); }
    public StringProperty usernameProperty() { return usernameProperty; }

    public String getColor() { return colorProperty.get(); }
    public void setColor(String c) { colorProperty.set(c != null ? c : "#00bcd4"); }
    public StringProperty colorProperty() { return colorProperty; }

    public String getDocumentId() { return documentIdProperty.get(); }
    public void setDocumentId(String d) { documentIdProperty.set(d != null ? d : ""); }
    public StringProperty documentIdProperty() { return documentIdProperty; }

    public int getLine() { return lineProperty.get(); }
    public void setLine(int line) { lineProperty.set(line); }
    public IntegerProperty lineProperty() { return lineProperty; }

    public int getColumn() { return columnProperty.get(); }
    public void setColumn(int col) { columnProperty.set(col); }
    public IntegerProperty columnProperty() { return columnProperty; }

    public int getSelStartLine() { return selStartLineProperty.get(); }
    public void setSelStartLine(int v) { selStartLineProperty.set(v); }
    public int getSelStartCol() { return selStartColProperty.get(); }
    public void setSelStartCol(int v) { selStartColProperty.set(v); }
    public int getSelEndLine() { return selEndLineProperty.get(); }
    public void setSelEndLine(int v) { selEndLineProperty.set(v); }
    public int getSelEndCol() { return selEndColProperty.get(); }
    public void setSelEndCol(int v) { selEndColProperty.set(v); }

    public boolean hasSelection() {
        return selStartLineProperty.get() >= 0 && selEndLineProperty.get() >= 0;
    }

    public void clearSelection() {
        selStartLineProperty.set(-1);
        selStartColProperty.set(-1);
        selEndLineProperty.set(-1);
        selEndColProperty.set(-1);
    }

    public String getDisplayName() {
        String u = getUsername();
        return (u != null && !u.isEmpty()) ? u : getClientId();
    }
}
