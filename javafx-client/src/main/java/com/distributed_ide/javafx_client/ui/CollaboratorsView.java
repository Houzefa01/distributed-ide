package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.RemoteCursor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;

/**
 * Liste des collaborateurs (curseurs multi-utilisateurs) sur le document courant.
 */
public class CollaboratorsView {

    private final BorderPane pane;
    private final ListView<RemoteCursor> listView;
    private final ObservableList<RemoteCursor> items = FXCollections.observableArrayList();

    public CollaboratorsView() {
        listView = new ListView<>(items);
        listView.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(RemoteCursor item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.getClientId() + " (" + item.getNodeId() + ")");
            }
        });
        pane = new BorderPane();
        pane.setTop(new Label("Collaborateurs"));
        pane.setCenter(listView);
    }

    public BorderPane getView() {
        return pane;
    }

    public void addCollaborator(String clientId, String nodeId) {
        if (clientId == null) return;
        if (items.stream().noneMatch(c -> clientId.equals(c.getClientId()))) {
            items.add(new RemoteCursor(clientId, nodeId));
        }
    }

    public void clearCollaborators() {
        items.clear();
    }

    public void setCollaboratorsForDocument(java.util.List<RemoteCursor> cursors) {
        items.clear();
        if (cursors != null) items.addAll(cursors);
    }
}
