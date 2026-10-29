package com.distributed_ide.plugin;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.InputStream;

/**
 * Metadata for a plugin, loaded from META-INF/plugin.json.
 */
public class PluginMetadata {
    
    private final String id;
    private final String name;
    private final String version;
    private final String description;
    private final String author;
    private final String mainClass;
    private final String minIdeVersion;
    private final String maxIdeVersion;
    private final String website;
    private final String license;
    
    @JsonCreator
    public PluginMetadata(
        @JsonProperty("id")  String id,
        @JsonProperty("name")  String name,
        @JsonProperty("version")  String version,
        @JsonProperty("description") String description,
        @JsonProperty("author") String author,
        @JsonProperty("mainClass")  String mainClass,
        @JsonProperty("minIdeVersion") String minIdeVersion,
        @JsonProperty("maxIdeVersion") String maxIdeVersion,
        @JsonProperty("website") String website,
        @JsonProperty("license") String license) {
        
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description != null ? description : "";
        this.author = author != null ? author : "";
        this.mainClass = mainClass;
        this.minIdeVersion = minIdeVersion;
        this.maxIdeVersion = maxIdeVersion;
        this.website = website != null ? website : "";
        this.license = license != null ? license : "";
    }
    
    /**
     * Parse plugin metadata from JSON input stream.
     * 
     * @param inputStream JSON input stream
     * @return Plugin metadata
     */
    
    public static PluginMetadata fromJson( InputStream inputStream) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(inputStream, PluginMetadata.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse plugin metadata", e);
        }
    }
    
    /**
     * Get the unique plugin ID.
     * 
     * @return Plugin ID
     */
    
    public String getId() {
        return id;
    }
    
    /**
     * Get the plugin name.
     * 
     * @return Plugin name
     */
    
    public String getName() {
        return name;
    }
    
    /**
     * Get the plugin version.
     * 
     * @return Plugin version
     */
    
    public String getVersion() {
        return version;
    }
    
    /**
     * Get the plugin description.
     * 
     * @return Plugin description
     */
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Get the plugin author.
     * 
     * @return Plugin author
     */
    
    public String getAuthor() {
        return author;
    }
    
    /**
     * Get the main class name.
     * 
     * @return Main class name
     */
    
    public String getMainClass() {
        return mainClass;
    }
    
    /**
     * Get the minimum IDE version required.
     * 
     * @return Minimum IDE version, or null if not specified
     */
    public String getMinIdeVersion() {
        return minIdeVersion;
    }
    
    /**
     * Get the maximum IDE version supported.
     * 
     * @return Maximum IDE version, or null if not specified
     */
    public String getMaxIdeVersion() {
        return maxIdeVersion;
    }
    
    /**
     * Get the plugin website.
     * 
     * @return Plugin website
     */
    
    public String getWebsite() {
        return website;
    }
    
    /**
     * Get the plugin license.
     * 
     * @return Plugin license
     */
    
    public String getLicense() {
        return license;
    }
    
    @Override
    public String toString() {
        return String.format("PluginMetadata{id='%s', name='%s', version='%s', author='%s'}", 
            id, name, version, author);
    }
}
