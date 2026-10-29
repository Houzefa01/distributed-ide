package com.distributed_ide.javafx_client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Inline find/replace bar — VS Code style.
 * Embeds at the top of the editor area instead of using a modal dialog.
 */
public class InlineFindBar extends VBox {

    private final TextField findField;
    private final TextField replaceField;
    private final Label matchCountLabel;
    private final HBox replaceRow;
    private final CheckBox caseSensitiveBox;
    private final CheckBox wholeWordBox;
    private final CheckBox regexBox;

    private Consumer<String> onFind;
    private Consumer<String> onFindNext;
    private Consumer<String> onFindPrev;
    private BiConsumer<String, String> onReplace;
    private BiConsumer<String, String> onReplaceAll;
    private Runnable onClose;

    private boolean replaceVisible = false;

    public InlineFindBar() {
        getStyleClass().add("inline-find-bar");
        setSpacing(4);
        setPadding(new Insets(4, 8, 4, 8));
        setManaged(false);
        setVisible(false);

        // --- Find row ---
        findField = new TextField();
        findField.setPromptText("Find");
        findField.getStyleClass().add("inline-find-field");
        findField.setPrefWidth(220);
        HBox.setHgrow(findField, Priority.NEVER);

        matchCountLabel = new Label("");
        matchCountLabel.getStyleClass().add("inline-find-count");
        matchCountLabel.setMinWidth(60);

        caseSensitiveBox = new CheckBox("Aa");
        caseSensitiveBox.getStyleClass().add("inline-find-option");
        caseSensitiveBox.setTooltip(new javafx.scene.control.Tooltip("Match Case"));

        wholeWordBox = new CheckBox("W");
        wholeWordBox.getStyleClass().add("inline-find-option");
        wholeWordBox.setTooltip(new javafx.scene.control.Tooltip("Whole Word"));

        regexBox = new CheckBox(".*");
        regexBox.getStyleClass().add("inline-find-option");
        regexBox.setTooltip(new javafx.scene.control.Tooltip("Use Regex"));

        Button prevBtn = new Button("↑");
        prevBtn.getStyleClass().add("inline-find-btn");
        prevBtn.setTooltip(new javafx.scene.control.Tooltip("Previous Match"));
        prevBtn.setOnAction(e -> { if (onFindPrev != null) onFindPrev.accept(findField.getText()); });

        Button nextBtn = new Button("↓");
        nextBtn.getStyleClass().add("inline-find-btn");
        nextBtn.setTooltip(new javafx.scene.control.Tooltip("Next Match"));
        nextBtn.setOnAction(e -> { if (onFindNext != null) onFindNext.accept(findField.getText()); });

        Button toggleReplaceBtn = new Button("▸");
        toggleReplaceBtn.getStyleClass().add("inline-find-btn");
        toggleReplaceBtn.setTooltip(new javafx.scene.control.Tooltip("Toggle Replace"));
        toggleReplaceBtn.setOnAction(e -> toggleReplace());

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("inline-find-btn");
        closeBtn.setOnAction(e -> hide());

        HBox findRow = new HBox(4, toggleReplaceBtn, findField, caseSensitiveBox, wholeWordBox, regexBox,
                matchCountLabel, prevBtn, nextBtn, closeBtn);
        findRow.setAlignment(Pos.CENTER_LEFT);

        // --- Replace row ---
        replaceField = new TextField();
        replaceField.setPromptText("Replace");
        replaceField.getStyleClass().add("inline-find-field");
        replaceField.setPrefWidth(220);

        Button replaceBtn = new Button("Replace");
        replaceBtn.getStyleClass().add("inline-find-btn");
        replaceBtn.setOnAction(e -> {
            if (onReplace != null) onReplace.accept(findField.getText(), replaceField.getText());
        });

        Button replaceAllBtn = new Button("Replace All");
        replaceAllBtn.getStyleClass().add("inline-find-btn");
        replaceAllBtn.setOnAction(e -> {
            if (onReplaceAll != null) onReplaceAll.accept(findField.getText(), replaceField.getText());
        });

        // Spacer to align replace field under find field (skip toggle button width)
        Label spacer = new Label("");
        spacer.setMinWidth(26);

        replaceRow = new HBox(4, spacer, replaceField, replaceBtn, replaceAllBtn);
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        replaceRow.setVisible(false);
        replaceRow.setManaged(false);

        getChildren().addAll(findRow, replaceRow);

        // --- Key bindings ---
        findField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (onFind != null && newVal != null) onFind.accept(newVal);
        });

        findField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    if (onFindPrev != null) onFindPrev.accept(findField.getText());
                } else {
                    if (onFindNext != null) onFindNext.accept(findField.getText());
                }
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hide();
                e.consume();
            }
        });

        replaceField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (onReplace != null) onReplace.accept(findField.getText(), replaceField.getText());
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hide();
                e.consume();
            }
        });
    }

    public void show() {
        setVisible(true);
        setManaged(true);
        findField.requestFocus();
        findField.selectAll();
    }

    public void show(String initialText) {
        if (initialText != null && !initialText.isEmpty()) {
            findField.setText(initialText);
        }
        show();
    }

    public void showWithReplace(String initialText) {
        if (!replaceVisible) {
            replaceVisible = true;
            replaceRow.setVisible(true);
            replaceRow.setManaged(true);
        }
        show(initialText);
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
        matchCountLabel.setText("");
        if (onClose != null) onClose.run();
    }

    public boolean isShowing() {
        return isVisible();
    }

    public void setMatchCount(int current, int total) {
        if (total == 0) {
            matchCountLabel.setText("Aucun résultat");
        } else {
            matchCountLabel.setText(current + " sur " + total);
        }
    }

    private void toggleReplace() {
        replaceVisible = !replaceVisible;
        replaceRow.setVisible(replaceVisible);
        replaceRow.setManaged(replaceVisible);
    }

    // --- Callbacks ---
    public void setOnFind(Consumer<String> handler) { this.onFind = handler; }
    public void setOnFindNext(Consumer<String> handler) { this.onFindNext = handler; }
    public void setOnFindPrev(Consumer<String> handler) { this.onFindPrev = handler; }
    public void setOnReplace(BiConsumer<String, String> handler) { this.onReplace = handler; }
    public void setOnReplaceAll(BiConsumer<String, String> handler) { this.onReplaceAll = handler; }
    public void setOnClose(Runnable handler) { this.onClose = handler; }

    public TextField getFindField() { return findField; }
}
