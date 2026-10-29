/* Test file for Java syntax highlighting verification */

public class SyntaxHighlightingTest {
    
    // Test class definition, method declaration, and various tokens
    private static final String TEST_STRING = "Hello, Syntax Highlighting!";
    private static final int TEST_NUMBER = 42;
    private static final boolean TEST_BOOLEAN = true;
    
    /**
     * Main method - Testing javadoc comments
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Single-line comment - should be green
        
        /* Multi-line comment
           should also be green */
        
        System.out.println("String literal - should be orange");
        System.out.println(TEST_NUMBER);  // Number should be purple
        
        // Test various keywords
        if (TEST_BOOLEAN) {
            for (int i = 0; i < 10; i++) {
                String result = "Concatenated: " + i;
                System.out.println(result);
            }
        }
        
        // Test method calls
        processData(TEST_STRING, TEST_NUMBER);
        
        // Test lambda (Java 8+)
        java.util.List<String> items = java.util.Arrays.asList("a", "b", "c");
        items.forEach(item -> System.out.println(item));
    }
    
    /**
     * Test method with various types
     */
    private static void processData(String text, int count) {
        // Test exception handling
        try {
            if (text == null) {
                throw new IllegalArgumentException("Text cannot be null");
            }
            
            for (int i = 0; i < count; i++) {
                // Nested block
            }
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Cleanup");
        }
    }
    
    // Test inner class
    private static class InnerClass {
        private String data;
        
        public InnerClass(String data) {
            this.data = data;
        }
    }
    
    // Test enum
    enum Color {
        RED, GREEN, BLUE
    }
    
    // Test annotations
    @Deprecated
    @Override
    public String toString() {
        return "SyntaxHighlightingTest{}";
    }
}
