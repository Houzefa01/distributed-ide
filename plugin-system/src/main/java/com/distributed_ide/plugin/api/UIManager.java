package com.distributed_ide.plugin.api;


import javafx.scene.Node;
import javafx.scene.control.MenuBar;

/**
 * Provides access to UI functionality for plugins.
 */
public interface UIManager {
    
    /**
     * Get the main menu bar.
     * 
     * @return Main menu bar
     */
    
    MenuBar getMenuBar();
    
    /**
     * Add a menu to the menu bar.
     * 
     * @param menu Menu to add
     * @return Registration handle
     */
    
    MenuRegistration addMenu( javafx.scene.control.Menu menu);
    
    /**
     * Add a menu item to an existing menu.
     * 
     * @param menuName Menu name
     * @param menuItem Menu item to add
     * @return Registration handle
     */
    
    MenuItemRegistration addMenuItem( String menuName,  javafx.scene.control.MenuItem menuItem);
    
    /**
     * Add a custom node to the status bar.
     * 
     * @param node Node to add
     * @return Registration handle
     */
    
    StatusBarRegistration addStatusBarNode( Node node);
    
    /**
     * Add a custom node to the toolbar.
     * 
     * @param node Node to add
     * @return Registration handle
     */
    
    ToolbarRegistration addToolbarNode( Node node);
    
    /**
     * Show a dialog.
     * 
     * @param dialog Dialog to show
     */
    void showDialog( javafx.stage.Stage dialog);
    
    /**
     * Show a notification toast.
     * 
     * @param title Notification title
     * @param message Notification message
     * @param type Notification type
     */
    void showNotification( String title,  String message,  NotificationType type);
    
    interface MenuRegistration {
        void remove();
    }
    
    interface MenuItemRegistration {
        void remove();
    }
    
    interface StatusBarRegistration {
        void remove();
    }
    
    interface ToolbarRegistration {
        void remove();
    }
    
    enum NotificationType {
        INFO, SUCCESS, WARNING, ERROR
    }
}
