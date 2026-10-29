package com.distributed_ide.javafx_client;

import com.distributed_ide.javafx_client.config.IDEConfig;
import com.distributed_ide.javafx_client.config.IDEErrorHandler;
import com.distributed_ide.javafx_client.error.GlobalExceptionHandler;
import com.distributed_ide.javafx_client.service.FileService;
import com.distributed_ide.javafx_client.service.ServiceShutdownRegistry;
import com.distributed_ide.javafx_client.theme.ThemeManager;
import com.distributed_ide.javafx_client.ui.MainLayout;
import com.distributed_ide.javafx_client.ui.MainLayoutController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedIDEApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(DistributedIDEApp.class);
    private MainLayoutController controller;

    @Override
    public void start(Stage primaryStage) {
        try {
            GlobalExceptionHandler globalErrorHandler = new GlobalExceptionHandler();
            globalErrorHandler.install();
            
            // Add FX thread exception handler
            Thread.currentThread().setUncaughtExceptionHandler((thread, exception) -> {
                globalErrorHandler.handleFxThreadException(exception);
            });

            // ✅ Initialize service shutdown registry for coordinated cleanup
            ServiceShutdownRegistry shutdownRegistry = ServiceShutdownRegistry.getInstance();
            
            // Centralized configuration
            IDEConfig config = IDEConfig.get();

            FileService fileService = new FileService();
            
            // ✅ Register FileService for atomic save cleanup
            shutdownRegistry.registerService("FileService", fileService::clearCache);
            
            MainLayout layout = new MainLayout(fileService);
            controller = new MainLayoutController(layout);
            
            // Register controller for shutdown coordination (all services shutdown via controller)
            shutdownRegistry.registerService("MainLayoutController", controller::shutdown);

            // Load config from persistent settings
            config.loadFrom(controller.getSettingsManager());
            controller.setSyncServerWsUri(config.getSyncServerUri());
            controller.setLspBridgeWsUri(config.getLspBridgeUri());
            controller.setTerminalWsUri(config.getTerminalUri());
            controller.setSignalingWsUri(config.getSignalingUri());

            // Wire global error handler to output view
            IDEErrorHandler.get().setOutputAppender((cat, msg) ->
                    layout.getOutputView().appendOutput(cat, msg));

            Scene scene = new Scene(layout.getView(), config.getWindowWidth(), config.getWindowHeight());

            // Theme manager: loads saved preference, swaps only theme stylesheet
            ThemeManager themeManager = new ThemeManager();
            controller.setThemeManager(themeManager);

            // Load base stylesheet (lowest priority — theme CSS overrides this)
            loadStylesheet(scene, "/styles.css");

            // Attach theme manager — applies saved theme CSS at end (highest priority)
            themeManager.attach(scene);

            primaryStage.setTitle("Hs_IDE");
            primaryStage.setScene(scene);
            
            //Improved shutdown: Use registry + exception handler
            primaryStage.setOnCloseRequest(e -> {
                try {
                    globalErrorHandler.markShuttingDown();
                    shutdownRegistry.shutdownAll();
                } catch (Exception ex) {
                    LOG.error("Error during shutdown", ex);
                }
            });
            
            primaryStage.setMaximized(true);
            primaryStage.show();
            
            // Restore workspace state (files, positions, dividers) after UI is visible
            controller.restoreWorkspaceState();
            LOG.info("IDE started successfully with global error handling");
        } catch (Exception e) {
            LOG.error("Failed to start IDE", e);
            throw new RuntimeException(e);
        }
    }

    private void loadStylesheet(Scene scene, String path) {
        try {
            var css = getClass().getResource(path);
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            } else {
                LOG.warn("Stylesheet not found: {}", path);
            }
        } catch (Exception e) {
            LOG.warn("Failed to load stylesheet: {}", path, e);
        }
    }

    public static void main(String[] args) {
        Application.launch(args);
    }
}
