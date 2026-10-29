package com.distributed_ide.plugin.api;


import java.util.function.Consumer;

/**
 * Descriptor for a command that can be executed in the IDE.
 */
public class CommandDescriptor {
    
    private final String id;
    private final String name;
    private final String description;
    private final Consumer<String> handler;
    private final String category;
    
    public CommandDescriptor( String id,  String name,  String description,  Consumer<String> handler) {
        this(id, name, description, handler, "General");
    }
    
    public CommandDescriptor( String id,  String name,  String description,  Consumer<String> handler,  String category) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.handler = handler;
        this.category = category;
    }
    
    /**
     * Get the unique command ID.
     * 
     * @return Command ID
     */
    
    public String getId() {
        return id;
    }
    
    /**
     * Get the command name.
     * 
     * @return Command name
     */
    
    public String getName() {
        return name;
    }
    
    /**
     * Get the command description.
     * 
     * @return Command description
     */
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the command handler.
     * 
     * @return Command handler
     */
    
    public Consumer<String> getHandler() {
        return handler;
    }
    
    /**
     * Get the command category.
     * 
     * @return Command category
     */
    
    public String getCategory() {
        return category;
    }
}
