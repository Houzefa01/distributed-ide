package com.distributed_ide.javafx_client.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;

/**
 * Modèle d'un onglet d'éditeur : chemin, contenu, état modifié.
 */
public class EditorTabModel {

    private final StringProperty pathProperty = new SimpleStringProperty("");
    private final StringProperty contentProperty = new SimpleStringProperty("");
    private final BooleanProperty dirtyProperty = new SimpleBooleanProperty(false);

    public EditorTabModel() {}

    public EditorTabModel(String path, String content) {
        pathProperty.set(path != null ? path : "");
        contentProperty.set(content != null ? content : "");
    }

    public static EditorTabModel fromPath(Path path) {
        String s = path != null ? path.toString() : "";
        return new EditorTabModel(s, "");
    }

    public String getPath() { return pathProperty.get(); }
    public void setPath(String path) { pathProperty.set(path); }
    public StringProperty pathProperty() { return pathProperty; }

    public String getContent() { return contentProperty.get(); }
    public void setContent(String content) { contentProperty.set(content); }
    public StringProperty contentProperty() { return contentProperty; }

    public boolean isDirty() { return dirtyProperty.get(); }
    public void setDirty(boolean dirty) { dirtyProperty.set(dirty); }
    public BooleanProperty dirtyProperty() { return dirtyProperty; }
    
    private volatile boolean updating = false;
    
    /**
     * Check if the model is currently being updated (for preventing recursive updates).
     */
    public boolean isUpdating() {
        return updating;
    }
    
    /**
     * Set the updating flag.
     */
    public void setUpdating(boolean updating) {
        this.updating = updating;
    }
    
    /**
     * Check if the tab has unsaved changes.
     */
    public boolean hasUnsavedChanges() {
        return isDirty();
    }
    
    /**
     * Mark the tab as having unsaved changes.
     */
    public void setUnsavedChanges(boolean unsaved) {
        setDirty(unsaved);
    }

    /** Nom court du fichier pour l'onglet. */
    public String getTabTitle() {
        String p = getPath();
        if (p == null || p.isEmpty()) return "Sans titre";
        int i = p.replace('\\', '/').lastIndexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }
}
