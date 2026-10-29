package com.distributed_ide.javafx_client.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * View for WebRTC video display.
 * Shows local and remote video streams.
 */
public class VideoCallView extends VBox {
    private static final Logger LOG = LoggerFactory.getLogger(VideoCallView.class);
    
    private final StackPane localVideoPane;
    private final StackPane remoteVideoPane;
    private final HBox controlsBox;
    private final Button muteButton;
    private final Button videoButton;
    private final Button hangupButton;
    private final Label statusLabel;
    
    private Consumer<Void> onMuteToggle;
    private Consumer<Void> onVideoToggle;
    private Consumer<Void> onHangup;
    
    private boolean isMuted = false;
    private boolean isVideoEnabled = true;
    
    public VideoCallView() {
        getStyleClass().add("video-call-view");
        setSpacing(10);
        setPadding(new Insets(10));
        
        // Remote video (large)
        remoteVideoPane = new StackPane();
        remoteVideoPane.getStyleClass().add("remote-video-pane");
        remoteVideoPane.setMinHeight(400);
        remoteVideoPane.setPrefHeight(400);
        
        // Placeholder for remote video
        Rectangle remotePlaceholder = new Rectangle(640, 480);
        remotePlaceholder.setFill(Color.rgb(30, 30, 30));
        remotePlaceholder.setArcWidth(8);
        remotePlaceholder.setArcHeight(8);
        Label remoteLabel = new Label("Remote Video");
        remoteLabel.getStyleClass().add("label-muted");
        remoteVideoPane.getChildren().addAll(remotePlaceholder, remoteLabel);
        
        // Local video (small, picture-in-picture)
        localVideoPane = new StackPane();
        localVideoPane.getStyleClass().add("local-video-pane");
        localVideoPane.setMaxSize(160, 120);
        localVideoPane.setPrefSize(160, 120);
        
        // Placeholder for local video
        Rectangle localPlaceholder = new Rectangle(160, 120);
        localPlaceholder.setFill(Color.rgb(40, 40, 40));
        localPlaceholder.setArcWidth(4);
        localPlaceholder.setArcHeight(4);
        Label localLabel = new Label("Local");
        localLabel.getStyleClass().add("label-muted");
        localVideoPane.getChildren().addAll(localPlaceholder, localLabel);
        
        // Position local video in corner of remote video
        StackPane.setAlignment(localVideoPane, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(localVideoPane, new Insets(10));
        remoteVideoPane.getChildren().add(localVideoPane);
        
        // Controls
        controlsBox = new HBox(10);
        controlsBox.getStyleClass().add("video-controls");
        controlsBox.setAlignment(Pos.CENTER);
        controlsBox.setPadding(new Insets(10));
        
        muteButton = new Button("Mute");
        muteButton.getStyleClass().add("video-control-btn");
        muteButton.setOnAction(e -> toggleMute());
        
        videoButton = new Button("Video Off");
        videoButton.getStyleClass().add("video-control-btn");
        videoButton.setOnAction(e -> toggleVideo());
        
        hangupButton = new Button("Hang Up");
        hangupButton.getStyleClass().addAll("video-control-btn", "hangup-btn");
        hangupButton.setOnAction(e -> hangup());
        
        controlsBox.getChildren().addAll(muteButton, videoButton, hangupButton);
        
        // Status label
        statusLabel = new Label("Not connected");
        statusLabel.getStyleClass().add("video-status-label");
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        
        getChildren().addAll(statusLabel, remoteVideoPane, controlsBox);
        
        setVisible(false);
    }
    
    private void toggleMute() {
        isMuted = !isMuted;
        muteButton.setText(isMuted ? "Unmute" : "Mute");
        if (onMuteToggle != null) {
            onMuteToggle.accept(null);
        }
    }
    
    private void toggleVideo() {
        isVideoEnabled = !isVideoEnabled;
        videoButton.setText(isVideoEnabled ? "Video Off" : "Video On");
        if (onVideoToggle != null) {
            onVideoToggle.accept(null);
        }
    }
    
    private void hangup() {
        if (onHangup != null) {
            onHangup.accept(null);
        }
        setVisible(false);
    }
    
    public void setStatus(String status) {
        statusLabel.setText(status);
    }
    
    public void showCall() {
        setVisible(true);
    }
    
    public void hideCall() {
        setVisible(false);
    }
    
    public void setOnMuteToggle(Consumer<Void> handler) {
        this.onMuteToggle = handler;
    }
    
    public void setOnVideoToggle(Consumer<Void> handler) {
        this.onVideoToggle = handler;
    }
    
    public void setOnHangup(Consumer<Void> handler) {
        this.onHangup = handler;
    }
    
    public StackPane getLocalVideoPane() {
        return localVideoPane;
    }
    
    public StackPane getRemoteVideoPane() {
        return remoteVideoPane;
    }
    
    public boolean isMuted() {
        return isMuted;
    }
    
    public boolean isVideoEnabled() {
        return isVideoEnabled;
    }
    
    /**
     * Attache un nœud de rendu vidéo au panneau local.
     * Pour intégration avec une bibliothèque WebRTC native.
     */
    public void attachLocalVideo(javafx.scene.Node videoNode) {
        localVideoPane.getChildren().clear();
        localVideoPane.getChildren().add(videoNode);
    }
    
    /**
     * Attache un nœud de rendu vidéo au panneau distant.
     * Pour intégration avec une bibliothèque WebRTC native.
     */
    public void attachRemoteVideo(javafx.scene.Node videoNode) {
        remoteVideoPane.getChildren().clear();
        remoteVideoPane.getChildren().add(videoNode);
        remoteVideoPane.getChildren().add(localVideoPane);
    }
    
    /**
     * Réinitialise les panneaux vidéo aux placeholders.
     */
    public void resetVideoPlaceholders() {
        localVideoPane.getChildren().clear();
        Rectangle localPlaceholder = new Rectangle(160, 120);
        localPlaceholder.setFill(Color.rgb(40, 40, 40));
        localPlaceholder.setArcWidth(4);
        localPlaceholder.setArcHeight(4);
        Label localLabel = new Label("Local");
        localLabel.getStyleClass().add("label-muted");
        localVideoPane.getChildren().addAll(localPlaceholder, localLabel);
        
        remoteVideoPane.getChildren().clear();
        Rectangle remotePlaceholder = new Rectangle(640, 480);
        remotePlaceholder.setFill(Color.rgb(30, 30, 30));
        remotePlaceholder.setArcWidth(8);
        remotePlaceholder.setArcHeight(8);
        Label remoteLabel = new Label("Remote Video");
        remoteLabel.getStyleClass().add("label-muted");
        remoteVideoPane.getChildren().addAll(remotePlaceholder, remoteLabel, localVideoPane);
    }
}
