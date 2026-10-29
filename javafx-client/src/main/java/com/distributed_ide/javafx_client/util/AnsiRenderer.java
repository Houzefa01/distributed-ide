package com.distributed_ide.javafx_client.util;

import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders ANSI escape codes as styled JavaFX Text nodes in a TextFlow.
 */
public class AnsiRenderer {
    
    private static final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[0-9;]*[a-zA-Z]");
    
    private static final Map<String, Color> ANSI_COLORS = new HashMap<>();
    static {
        // Standard colors
        ANSI_COLORS.put("30", Color.BLACK);
        ANSI_COLORS.put("31", Color.RED);
        ANSI_COLORS.put("32", Color.GREEN);
        ANSI_COLORS.put("33", Color.YELLOW);
        ANSI_COLORS.put("34", Color.BLUE);
        ANSI_COLORS.put("35", Color.MAGENTA);
        ANSI_COLORS.put("36", Color.CYAN);
        ANSI_COLORS.put("37", Color.WHITE);
        
        // Bright colors
        ANSI_COLORS.put("90", Color.DARKGRAY);
        ANSI_COLORS.put("91", Color.rgb(255, 85, 85));
        ANSI_COLORS.put("92", Color.rgb(85, 255, 85));
        ANSI_COLORS.put("93", Color.rgb(255, 255, 85));
        ANSI_COLORS.put("94", Color.rgb(85, 85, 255));
        ANSI_COLORS.put("95", Color.rgb(255, 85, 255));
        ANSI_COLORS.put("96", Color.rgb(85, 255, 255));
        ANSI_COLORS.put("97", Color.rgb(255, 255, 255));
    }
    
    private static final Map<String, Color> ANSI_BG_COLORS = new HashMap<>();
    static {
        ANSI_BG_COLORS.put("40", Color.BLACK);
        ANSI_BG_COLORS.put("41", Color.RED);
        ANSI_BG_COLORS.put("42", Color.GREEN);
        ANSI_BG_COLORS.put("43", Color.YELLOW);
        ANSI_BG_COLORS.put("44", Color.BLUE);
        ANSI_BG_COLORS.put("45", Color.MAGENTA);
        ANSI_BG_COLORS.put("46", Color.CYAN);
        ANSI_BG_COLORS.put("47", Color.WHITE);
    }
    
    /**
     * Render text with ANSI codes into a TextFlow.
     */
    public static void renderToTextFlow(String text, TextFlow textFlow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        
        textFlow.getChildren().addAll(parseAnsiText(text));
    }
    
    /**
     * Parse ANSI text and return styled Text nodes.
     */
    public static java.util.List<Text> parseAnsiText(String text) {
        java.util.List<Text> nodes = new java.util.ArrayList<>();
        
        if (text == null || text.isEmpty()) {
            return nodes;
        }
        
        Matcher matcher = ANSI_PATTERN.matcher(text);
        int lastEnd = 0;
        
        Color currentFgColor = null;
        Color currentBgColor = null;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        
        while (matcher.find()) {
            // Add text before this ANSI code
            if (matcher.start() > lastEnd) {
                String segment = text.substring(lastEnd, matcher.start());
                Text textNode = createStyledText(segment, currentFgColor, currentBgColor, bold, italic, underline);
                nodes.add(textNode);
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
                    case "22": bold = false; break;
                    case "23": italic = false; break;
                    case "24": underline = false; break;
                    default:
                        if (ANSI_COLORS.containsKey(part)) {
                            currentFgColor = ANSI_COLORS.get(part);
                        } else if (ANSI_BG_COLORS.containsKey(part)) {
                            currentBgColor = ANSI_BG_COLORS.get(part);
                        }
                        break;
                }
            }
            
            lastEnd = matcher.end();
        }
        
        // Add remaining text
        if (lastEnd < text.length()) {
            String segment = text.substring(lastEnd);
            Text textNode = createStyledText(segment, currentFgColor, currentBgColor, bold, italic, underline);
            nodes.add(textNode);
        }
        
        return nodes;
    }
    
    private static String[] parseAnsiCode(String code) {
        // Extract numbers from escape sequence: \u001B[31;1m -> ["31", "1"]
        String numbers = code.substring(2, code.length() - 1);
        if (numbers.isEmpty()) {
            return new String[]{"0"};
        }
        return numbers.split(";");
    }
    
    private static Text createStyledText(String content, Color fgColor, Color bgColor, 
                                        boolean bold, boolean italic, boolean underline) {
        Text text = new Text(content);
        
        if (fgColor != null) {
            text.setFill(fgColor);
        }
        
        // Background color via CSS style
        if (bgColor != null) {
            String bgHex = String.format("#%02X%02X%02X", 
                (int)(bgColor.getRed() * 255),
                (int)(bgColor.getGreen() * 255),
                (int)(bgColor.getBlue() * 255));
            text.setStyle("-fx-background-color: " + bgHex + ";");
        }
        
        // Font styling
        if (bold || italic) {
            FontWeight weight = bold ? FontWeight.BOLD : FontWeight.NORMAL;
            FontPosture posture = italic ? FontPosture.ITALIC : FontPosture.REGULAR;
            text.setFont(Font.font("Monospaced", weight, posture, 12));
        } else {
            text.setFont(Font.font("Monospaced", 12));
        }
        
        if (underline) {
            text.setUnderline(true);
        }
        
        return text;
    }
    
    /**
     * Strip all ANSI escape codes from text.
     */
    public static String stripAnsiCodes(String text) {
        if (text == null) {
            return null;
        }
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }
}
