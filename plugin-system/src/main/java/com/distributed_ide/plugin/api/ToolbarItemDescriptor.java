package com.distributed_ide.plugin.api;


import javafx.scene.Node;

/**
 * Descriptor for a toolbar item to be added to the IDE toolbar.
 */
public class ToolbarItemDescriptor {
    
    private final Node node;
    private final int position;
    private final String section;
    
    public ToolbarItemDescriptor( Node node) {
        this(node, -1, "main");
    }
    
    public ToolbarItemDescriptor( Node node, int position) {
        this(node, position, "main");
    }
    
    public ToolbarItemDescriptor( Node node, int position,  String section) {
        this.node = node;
        this.position = position;
        this.section = section;
    }
    
    /**
     * Get the toolbar node.
     * 
     * @return Toolbar node
     */
    
    public Node getNode() {
        return node;
    }
    
    /**
     * Get the position in the toolbar section (-1 for end).
     * 
     * @return Position index
     */
    public int getPosition() {
        return position;
    }
    
    /**
     * Get the toolbar section.
     * 
     * @return Toolbar section
     */
    
    public String getSection() {
        return section;
    }
}
