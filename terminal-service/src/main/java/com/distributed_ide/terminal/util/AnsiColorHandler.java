package com.distributed_ide.terminal.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for handling ANSI escape codes in terminal output.
 * Supports color codes, cursor movement, and text formatting.
 */
public class AnsiColorHandler {
    
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*[a-zA-Z]");
    
    // ANSI color codes
    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    
    // Bright colors
    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_MAGENTA = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";
    
    // Background colors
    public static final String BG_BLACK = "\u001B[40m";
    public static final String BG_RED = "\u001B[41m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_MAGENTA = "\u001B[45m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_WHITE = "\u001B[47m";
    
    // Text formatting
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String ITALIC = "\u001B[3m";
    public static final String UNDERLINE = "\u001B[4m";
    public static final String BLINK = "\u001B[5m";
    public static final String REVERSE = "\u001B[7m";
    public static final String HIDDEN = "\u001B[8m";
    public static final String STRIKETHROUGH = "\u001B[9m";
    
    /**
     * Strip all ANSI escape codes from text.
     */
    public static String stripAnsiCodes(String text) {
        if (text == null) {
            return null;
        }
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }
    
    /**
     * Check if text contains ANSI codes.
     */
    public static boolean containsAnsiCodes(String text) {
        if (text == null) {
            return false;
        }
        return ANSI_PATTERN.matcher(text).find();
    }
    
    /**
     * Parse ANSI codes and convert to structured format for JavaFX rendering.
     * Returns a list of text segments with their associated styles.
     */
    public static java.util.List<AnsiSegment> parseAnsiText(String text) {
        java.util.List<AnsiSegment> segments = new java.util.ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return segments;
        }
        
        Matcher matcher = ANSI_PATTERN.matcher(text);
        int lastEnd = 0;
        String currentFgColor = null;
        String currentBgColor = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        
        while (matcher.find()) {
            // Add text before this ANSI code
            if (matcher.start() > lastEnd) {
                String segment = text.substring(lastEnd, matcher.start());
                segments.add(new AnsiSegment(segment, currentFgColor, currentBgColor, bold, italic, underline));
            }
            
            // Parse ANSI code
            String code = matcher.group();
            String[] parts = parseAnsiCode(code);
            
            for (String part : parts) {
                switch (part) {
                    case "0": // Reset
                        currentFgColor = null;
                        currentBgColor = null;
                        bold = false;
                        italic = false;
                        underline = false;
                        break;
                    case "1": bold = true; break;
                    case "3": italic = true; break;
                    case "4": underline = true; break;
                    case "30": currentFgColor = "black"; break;
                    case "31": currentFgColor = "red"; break;
                    case "32": currentFgColor = "green"; break;
                    case "33": currentFgColor = "yellow"; break;
                    case "34": currentFgColor = "blue"; break;
                    case "35": currentFgColor = "magenta"; break;
                    case "36": currentFgColor = "cyan"; break;
                    case "37": currentFgColor = "white"; break;
                    case "90": currentFgColor = "bright-black"; break;
                    case "91": currentFgColor = "bright-red"; break;
                    case "92": currentFgColor = "bright-green"; break;
                    case "93": currentFgColor = "bright-yellow"; break;
                    case "94": currentFgColor = "bright-blue"; break;
                    case "95": currentFgColor = "bright-magenta"; break;
                    case "96": currentFgColor = "bright-cyan"; break;
                    case "97": currentFgColor = "bright-white"; break;
                    case "40": currentBgColor = "black"; break;
                    case "41": currentBgColor = "red"; break;
                    case "42": currentBgColor = "green"; break;
                    case "43": currentBgColor = "yellow"; break;
                    case "44": currentBgColor = "blue"; break;
                    case "45": currentBgColor = "magenta"; break;
                    case "46": currentBgColor = "cyan"; break;
                    case "47": currentBgColor = "white"; break;
                }
            }
            
            lastEnd = matcher.end();
        }
        
        // Add remaining text
        if (lastEnd < text.length()) {
            String segment = text.substring(lastEnd);
            segments.add(new AnsiSegment(segment, currentFgColor, currentBgColor, bold, italic, underline));
        }
        
        return segments;
    }
    
    private static String[] parseAnsiCode(String code) {
        // Extract numbers from escape sequence: \u001B[31;1m -> ["31", "1"]
        String numbers = code.substring(2, code.length() - 1);
        if (numbers.isEmpty()) {
            return new String[]{"0"};
        }
        return numbers.split(";");
    }
    
    /**
     * Represents a text segment with ANSI styling.
     */
    public static class AnsiSegment {
        private final String text;
        private final String foregroundColor;
        private final String backgroundColor;
        private final boolean bold;
        private final boolean italic;
        private final boolean underline;
        
        public AnsiSegment(String text, String fgColor, String bgColor, 
                          boolean bold, boolean italic, boolean underline) {
            this.text = text;
            this.foregroundColor = fgColor;
            this.backgroundColor = bgColor;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
        }
        
        public String getText() { return text; }
        public String getForegroundColor() { return foregroundColor; }
        public String getBackgroundColor() { return backgroundColor; }
        public boolean isBold() { return bold; }
        public boolean isItalic() { return italic; }
        public boolean isUnderline() { return underline; }
        
        @Override
        public String toString() {
            return String.format("AnsiSegment{text='%s', fg=%s, bg=%s, bold=%s, italic=%s, underline=%s}",
                text, foregroundColor, backgroundColor, bold, italic, underline);
        }
    }
    
    /**
     * Colorize text with ANSI codes.
     */
    public static String colorize(String text, String color) {
        if (text == null) return null;
        String colorCode = switch (color.toLowerCase()) {
            case "red" -> RED;
            case "green" -> GREEN;
            case "yellow" -> YELLOW;
            case "blue" -> BLUE;
            case "magenta" -> MAGENTA;
            case "cyan" -> CYAN;
            case "white" -> WHITE;
            case "black" -> BLACK;
            default -> "";
        };
        return colorCode + text + RESET;
    }
}
