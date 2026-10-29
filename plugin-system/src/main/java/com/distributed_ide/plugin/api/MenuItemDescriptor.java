package com.distributed_ide.plugin.api;


import javafx.scene.control.MenuItem;

/**
 * Descriptor for a menu item to be added to the IDE menu.
 */
public class MenuItemDescriptor {
    
    private final String menuName;
    private final MenuItem menuItem;
    private final int position;
    
    public MenuItemDescriptor( String menuName,  MenuItem menuItem) {
        this(menuName, menuItem, -1);
    }
    
    public MenuItemDescriptor( String menuName,  MenuItem menuItem, int position) {
        this.menuName = menuName;
        this.menuItem = menuItem;
        this.position = position;
    }
    
    /**
     * Get the name of the menu to add this item to.
     * 
     * @return Menu name
     */
    
    public String getMenuName() {
        return menuName;
    }
    
    /**
     * Get the menu item to add.
     * 
     * @return Menu item
     */
    
    public MenuItem getMenuItem() {
        return menuItem;
    }
    
    /**
     * Get the position in the menu (-1 for end).
     * 
     * @return Position index
     */
    public int getPosition() {
        return position;
    }
}
