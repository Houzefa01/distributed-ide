package com.distributed_ide.javafx_client.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import java.util.Optional;

/**
 * Dialog to host or join a LAN collaboration server.
 * Returns a Result containing the mode (HOST/JOIN) and parameters.
 */
public class HostJoinDialog extends Dialog<HostJoinDialog.Result> {

    public enum Mode { HOST, JOIN }

    public static class Result {
        public final Mode mode;
        public final String host;
        public final int port;
        public final String token;
        public final String username;

        public Result(Mode mode, String host, int port, String token, String username) {
            this.mode = mode;
            this.host = host;
            this.port = port;
            this.token = token;
            this.username = username;
        }
    }

    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton hostRadio = new RadioButton("Host a server");
    private final RadioButton joinRadio = new RadioButton("Join a server");
    private final TextField usernameField = new TextField("Admin");
    private final TextField portField = new TextField("8765");
    private final TextField hostField = new TextField("localhost");
    private final TextField tokenField = new TextField("");

    public HostJoinDialog() {
        setTitle("LAN Collaboration");
        setHeaderText("Start or join a collaborative session");

        hostRadio.setToggleGroup(modeGroup);
        joinRadio.setToggleGroup(modeGroup);
        hostRadio.setSelected(true);

        Label hostLabel = new Label("IP Address:");
        Label tokenLabel = new Label("Token:");
        hostField.setDisable(true);
        tokenField.setDisable(true);

        modeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isJoin = newVal == joinRadio;
            hostField.setDisable(!isJoin);
            tokenField.setDisable(!isJoin);
            if (!isJoin) {
                usernameField.setText("Admin");
            } else {
                usernameField.setText("Client");
            }
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 20));

        HBox modeBox = new HBox(20, hostRadio, joinRadio);
        grid.add(modeBox, 0, 0, 2, 1);
        grid.add(new Label("Username:"), 0, 1);
        grid.add(usernameField, 1, 1);
        grid.add(new Label("Port:"), 0, 2);
        grid.add(portField, 1, 2);
        grid.add(hostLabel, 0, 3);
        grid.add(hostField, 1, 3);
        grid.add(tokenLabel, 0, 4);
        grid.add(tokenField, 1, 4);

        getDialogPane().setContent(grid);

        ButtonType connectType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(connectType, cancelType);

        setResultConverter(button -> {
            if (button == connectType) {
                Mode mode = hostRadio.isSelected() ? Mode.HOST : Mode.JOIN;
                int port;
                try {
                    port = Integer.parseInt(portField.getText().trim());
                } catch (NumberFormatException e) {
                    port = 8765;
                }
                return new Result(
                        mode,
                        hostField.getText().trim(),
                        port,
                        tokenField.getText().trim(),
                        usernameField.getText().trim()
                );
            }
            return null;
        });
    }

    /**
     * Shows the dialog and returns the result.
     */
    public static Optional<Result> showAndGet() {
        HostJoinDialog dialog = new HostJoinDialog();
        return dialog.showAndWait();
    }
}
