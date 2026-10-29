package com.distributed_ide.javafx_client.ui;

import com.distributed_ide.javafx_client.model.DiagnosticItem;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.BiConsumer;

/** LSP diagnostics view. */
public class DiagnosticsView {

    private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsView.class);

    private final TableView<DiagnosticItem> table;
    private final ObservableList<DiagnosticItem> items = FXCollections.observableArrayList();
    private BiConsumer<String, Integer> onGoToLine;

    public DiagnosticsView() {
        table = new TableView<>(items);
        table.setPrefHeight(200);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        
        TableColumn<DiagnosticItem, String> fileCol = new TableColumn<>("File");
        fileCol.setCellValueFactory(c -> c.getValue().uriProperty());
        fileCol.setPrefWidth(180);
        TableColumn<DiagnosticItem, Integer> lineCol = new TableColumn<>("Line");
        lineCol.setCellValueFactory(c -> c.getValue().lineProperty().asObject());
        lineCol.setPrefWidth(50);
        TableColumn<DiagnosticItem, String> sevCol = new TableColumn<>("Type");
        sevCol.setCellValueFactory(c -> c.getValue().severityProperty());
        sevCol.setPrefWidth(70);
        TableColumn<DiagnosticItem, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(c -> c.getValue().messageProperty());
        msgCol.setPrefWidth(350);
        table.getColumns().addAll(fileCol, lineCol, sevCol, msgCol);
        table.setOnMouseClicked(e -> {
            DiagnosticItem d = table.getSelectionModel().getSelectedItem();
            if (d != null && onGoToLine != null) onGoToLine.accept(d.getUri(), d.getLine());
        });
    }

    public TableView<DiagnosticItem> getView() { return table; }

    public void setDiagnostics(String uri, List<DiagnosticItem> list) {
        LOG.info("[DiagnosticsView] setDiagnostics called: uri={}, count={}", uri, list != null ? list.size() : 0);
        items.removeIf(d -> uri != null && uri.equals(d.getUri()));
        if (list != null) {
            LOG.info("[DiagnosticsView] Adding {} items for URI {}", list.size(), uri);
            for (DiagnosticItem d : list) {
                LOG.info("[DiagnosticsView]   - Line {}: {} - {}", d.getLine(), d.getSeverity(), d.getMessage());
            }
            items.addAll(list);
        }
        LOG.info("[DiagnosticsView] Total items now in table: {}", items.size());
    }

    public void setOnGoToLine(BiConsumer<String, Integer> handler) { this.onGoToLine = handler; }
}
