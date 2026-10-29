package com.distributed_ide.plugin.api;


import java.util.List;

/**
 * Provides access to theme functionality for plugins.
 */
public interface ThemeManager {
    
    /**
     * Get the current theme.
     * 
     * @return Current theme name
     */
    
    String getCurrentTheme();
    
    /**
     * Set the current theme.
     * 
     * @param themeName Theme name to set
     */
    void setCurrentTheme( String themeName);
    
    /**
     * Get all available themes.
     * 
     * @return List of available theme names
     */
    
    List<String> getAvailableThemes();
    
    /**
     * Register a new theme.
     * 
     * @param theme Theme to register
     * @return Registration handle
     */
    
    ThemeRegistration registerTheme( Theme theme);
    
    interface Theme {
        
        String getName();
        
        
        String getDisplayName();
        
        boolean isDark();
        
        
        String getStylesheetPath();
    }
    
    interface ThemeRegistration {
        void remove();
    }
}
