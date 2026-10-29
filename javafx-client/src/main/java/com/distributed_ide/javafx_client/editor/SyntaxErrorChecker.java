package com.distributed_ide.javafx_client.editor;

import org.jsoup.Jsoup;
import org.jsoup.parser.ParseErrorList;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Async syntax error checker for Java, Python, and HTML.
 *
 * - Java: uses javax.tools.JavaCompiler (in-memory, no disk I/O)
 * - Python: uses ProcessBuilder to run "python3 -c compile(...)"
 * - HTML: regex-based tag balancing and common structural errors
 *
 * All checks run off the FX thread. Results are returned as a list of SyntaxError.
 */
public class SyntaxErrorChecker {

    private static final Logger LOG = LoggerFactory.getLogger(SyntaxErrorChecker.class);

    private static final ExecutorService CHECKER_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "syntax-checker");
        t.setDaemon(true);
        return t;
    });

    /**
     * Represents a single syntax error with line, column, and message.
     */
    public static class SyntaxError {
        private final int line;
        private final int column;
        private final String message;
        private final Severity severity;
        private final int startOffset;
        private final int endOffset;

        public enum Severity { ERROR, WARNING }

        public SyntaxError(int line, int column, String message, Severity severity) {
            this(line, column, message, severity, -1, -1);
        }

        public SyntaxError(int line, int column, String message, Severity severity,
                           int startOffset, int endOffset) {
            this.line = line;
            this.column = column;
            this.message = message;
            this.severity = severity;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
        }

        public int getLine() { return line; }
        public int getColumn() { return column; }
        public String getMessage() { return message; }
        public Severity getSeverity() { return severity; }
        public int getStartOffset() { return startOffset; }
        public int getEndOffset() { return endOffset; }
        public boolean hasOffsets() { return startOffset >= 0 && endOffset > startOffset; }

        @Override
        public String toString() {
            return severity + " [" + line + ":" + column + "] " + message;
        }
    }

    /**
     * Check syntax errors for the given language.
     * Returns a future list of errors (empty if no errors).
     */
    public static CompletableFuture<List<SyntaxError>> checkAsync(String code, String language, String filePath) {
        if (code == null || code.isBlank() || language == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return switch (language.toLowerCase()) {
                    case "java" -> checkJava(code, filePath);
                    case "python" -> checkPython(code);
                    case "html", "htm" -> checkHtml(code);
                    default -> Collections.<SyntaxError>emptyList();
                };
            } catch (Exception e) {
                LOG.warn("Syntax check failed for {}", language, e);
                return Collections.<SyntaxError>emptyList();
            }
        }, CHECKER_EXECUTOR);
    }

    // ═══════════════════════════════════════════════════════════
    //  JAVA — javax.tools in-memory compilation
    // ═══════════════════════════════════════════════════════════

    private static List<SyntaxError> checkJava(String code, String filePath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            LOG.debug("No system Java compiler available (running on JRE?)");
            return Collections.emptyList();
        }

        String className = extractClassName(code, filePath);
        JavaFileObject source = new InMemoryJavaSource(className, code);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        // Compile with no output (syntax check only)
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics,
                List.of("-proc:none", "-implicit:none", "-Xlint:none"),
                null, List.of(source));

        try {
            task.call();
        } catch (Exception e) {
            LOG.debug("Java compilation check threw: {}", e.getMessage());
        }

        List<SyntaxError> errors = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR || d.getKind() == Diagnostic.Kind.WARNING) {
                SyntaxError.Severity sev = d.getKind() == Diagnostic.Kind.ERROR
                        ? SyntaxError.Severity.ERROR : SyntaxError.Severity.WARNING;

                int startOff = (int) d.getStartPosition();
                int endOff = (int) d.getEndPosition();
                // If the compiler didn't give us a range, underline the whole line
                if (startOff == Diagnostic.NOPOS || endOff == Diagnostic.NOPOS) {
                    int line = (int) d.getLineNumber();
                    int[] lineRange = getLineOffsets(code, line);
                    startOff = lineRange[0];
                    endOff = lineRange[1];
                }

                errors.add(new SyntaxError(
                        (int) d.getLineNumber(),
                        (int) d.getColumnNumber(),
                        d.getMessage(Locale.ENGLISH),
                        sev,
                        startOff,
                        endOff));
            }
        }

        try { fileManager.close(); } catch (IOException ignored) {}
        return errors;
    }

    /**
     * Returns [startOffset, endOffset] for the given 1-based line number.
     */
    private static int[] getLineOffsets(String code, int lineNumber) {
        int line = 1;
        int start = 0;
        for (int i = 0; i < code.length(); i++) {
            if (line == lineNumber) {
                start = i;
                int end = code.indexOf('\n', i);
                return new int[]{start, end < 0 ? code.length() : end};
            }
            if (code.charAt(i) == '\n') line++;
        }
        return new int[]{0, Math.min(code.length(), 1)};
    }

    private static String extractClassName(String code, String filePath) {
        if (filePath != null) {
            String name = Path.of(filePath).getFileName().toString();
            if (name.endsWith(".java")) return name.substring(0, name.length() - 5);
        }
        Matcher m = Pattern.compile("(?:class|interface|enum|record)\\s+(\\w+)").matcher(code);
        return m.find() ? m.group(1) : "Source";
    }

    /**
     * In-memory JavaFileObject for javax.tools compilation.
     */
    private static class InMemoryJavaSource extends SimpleJavaFileObject {
        private final String code;

        InMemoryJavaSource(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  PYTHON — ProcessBuilder compile() check
    // ═══════════════════════════════════════════════════════════

    private static List<SyntaxError> checkPython(String code) {
        // Use ast.parse via subprocess for detailed error info (line, col, end_line, end_col)
        try {
            String pythonCmd = findPython();
            if (pythonCmd == null) {
                LOG.debug("No Python interpreter found");
                return Collections.emptyList();
            }

            // Python script that attempts ast.parse and outputs structured error info
            String script = String.join("\n",
                    "import sys, json, ast",
                    "code = sys.stdin.read()",
                    "try:",
                    "    ast.parse(code)",
                    "    print(json.dumps([]))",
                    "except SyntaxError as e:",
                    "    err = {'line': e.lineno or 1, 'col': e.offset or 1,",
                    "           'end_line': getattr(e, 'end_lineno', None) or (e.lineno or 1),",
                    "           'end_col': getattr(e, 'end_offset', None) or ((e.offset or 1) + 1),",
                    "           'msg': str(e.msg) if hasattr(e, 'msg') else str(e)}",
                    "    print(json.dumps([err]))"
            );

            ProcessBuilder pb = new ProcessBuilder(pythonCmd, "-c", script);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Write code to stdin
            try (OutputStream os = process.getOutputStream()) {
                os.write(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            }

            // Read output
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                output = sb.toString().trim();
            }

            process.waitFor();
            if (output.isEmpty()) {
                return Collections.emptyList();
            }

            return parsePythonJsonErrors(output, code);

        } catch (Exception e) {
            LOG.debug("Python syntax check failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String findPython() {
        for (String cmd : new String[]{"python3", "python"}) {
            try {
                Process p = new ProcessBuilder(cmd, "--version")
                        .redirectErrorStream(true).start();
                int exit = p.waitFor();
                if (exit == 0) return cmd;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Parse JSON error output from the Python ast.parse script.
     * Falls back to regex parsing if JSON parsing fails.
     */
    private static List<SyntaxError> parsePythonJsonErrors(String output, String code) {
        List<SyntaxError> errors = new ArrayList<>();
        try {
            // Simple JSON array parsing without dependency on Jackson
            if (!output.startsWith("[")) {
                // Fallback: not JSON, parse as raw error text
                return parsePythonRawError(output, code);
            }
            // Parse JSON manually (simple single-object array)
            // Format: [{"line":N,"col":N,"end_line":N,"end_col":N,"msg":"..."}]
            if (output.equals("[]")) return errors;

            // Extract fields with regex from the JSON
            Pattern lineP = Pattern.compile("\"line\"\\s*:\\s*(\\d+)");
            Pattern colP = Pattern.compile("\"col\"\\s*:\\s*(\\d+)");
            Pattern endLineP = Pattern.compile("\"end_line\"\\s*:\\s*(\\d+)");
            Pattern endColP = Pattern.compile("\"end_col\"\\s*:\\s*(\\d+)");
            Pattern msgP = Pattern.compile("\"msg\"\\s*:\\s*\"([^\"]*)\"");

            Matcher lm = lineP.matcher(output);
            Matcher cm = colP.matcher(output);
            Matcher elm = endLineP.matcher(output);
            Matcher ecm = endColP.matcher(output);
            Matcher mm = msgP.matcher(output);

            int line = lm.find() ? Integer.parseInt(lm.group(1)) : 1;
            int col = cm.find() ? Integer.parseInt(cm.group(1)) : 1;
            int endLine = elm.find() ? Integer.parseInt(elm.group(1)) : line;
            int endCol = ecm.find() ? Integer.parseInt(ecm.group(1)) : col + 1;
            String msg = mm.find() ? mm.group(1) : "SyntaxError";

            // Convert line:col to absolute offsets
            int startOff = lineColToOffset(code, line, col);
            int endOff = lineColToOffset(code, endLine, endCol);
            if (endOff <= startOff) endOff = startOff + 1;

            errors.add(new SyntaxError(line, col, msg, SyntaxError.Severity.ERROR, startOff, endOff));
        } catch (Exception e) {
            LOG.debug("Failed to parse Python JSON errors: {}", e.getMessage());
            return parsePythonRawError(output, code);
        }
        return errors;
    }

    private static final Pattern PY_ERROR_PATTERN =
            Pattern.compile("File \"<editor>\", line (\\d+)");

    private static List<SyntaxError> parsePythonRawError(String output, String code) {
        List<SyntaxError> errors = new ArrayList<>();
        Matcher m = PY_ERROR_PATTERN.matcher(output);
        int line = 1;
        if (m.find()) {
            line = Integer.parseInt(m.group(1));
        }
        String[] lines = output.split("\n");
        String message = lines[lines.length - 1].trim();
        if (!message.isEmpty()) {
            int[] offsets = getLineOffsets(code, line);
            errors.add(new SyntaxError(line, 1, message, SyntaxError.Severity.ERROR,
                    offsets[0], offsets[1]));
        }
        return errors;
    }

    /**
     * Converts 1-based line and 1-based column to a 0-based absolute offset in the code string.
     */
    private static int lineColToOffset(String code, int line, int col) {
        int currentLine = 1;
        for (int i = 0; i < code.length(); i++) {
            if (currentLine == line) {
                return Math.min(i + col - 1, code.length());
            }
            if (code.charAt(i) == '\n') currentLine++;
        }
        return code.length();
    }

    // ═══════════════════════════════════════════════════════════
    //  HTML — Jsoup-based professional validation
    // ═══════════════════════════════════════════════════════════

    private static final Set<String> VOID_ELEMENTS = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
    );

    private static final Pattern HTML_TAG_PATTERN =
            Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*?)(/?)>");

    private static List<SyntaxError> checkHtml(String code) {
        List<SyntaxError> errors = new ArrayList<>();

        // Phase 1: Jsoup parse error collection
        try {
            Parser parser = Parser.htmlParser().setTrackErrors(100);
            Jsoup.parse(code, "", parser);
            ParseErrorList parseErrors = parser.getErrors();

            for (org.jsoup.parser.ParseError pe : parseErrors) {
                int pos = pe.getPosition();
                String msg = pe.getErrorMessage();

                // Convert absolute position to line:col
                int line = 1;
                int col = 1;
                for (int i = 0; i < pos && i < code.length(); i++) {
                    if (code.charAt(i) == '\n') {
                        line++;
                        col = 1;
                    } else {
                        col++;
                    }
                }

                // Determine error extent: underline to end of current token/tag
                int startOff = Math.min(pos, code.length());
                int endOff = startOff;
                if (startOff < code.length()) {
                    // Try to find the end of the tag or token
                    if (code.charAt(startOff) == '<') {
                        int closeIdx = code.indexOf('>', startOff);
                        endOff = closeIdx >= 0 ? closeIdx + 1 : Math.min(startOff + 20, code.length());
                    } else {
                        // Underline the word/token
                        endOff = startOff;
                        while (endOff < code.length() && !Character.isWhitespace(code.charAt(endOff))
                                && code.charAt(endOff) != '<' && code.charAt(endOff) != '>') {
                            endOff++;
                        }
                        if (endOff == startOff) endOff = Math.min(startOff + 1, code.length());
                    }
                }

                SyntaxError.Severity severity = msg.toLowerCase().contains("unexpected")
                        || msg.toLowerCase().contains("not found")
                        ? SyntaxError.Severity.ERROR : SyntaxError.Severity.WARNING;

                errors.add(new SyntaxError(line, col, msg, severity, startOff, endOff));
            }
        } catch (Exception e) {
            LOG.debug("Jsoup parse failed, falling back to tag-balance check: {}", e.getMessage());
        }

        // Phase 2: Tag-balance validation (catches unclosed/mismatched tags Jsoup auto-corrects)
        errors.addAll(checkHtmlTagBalance(code));

        // Phase 3: Structural checks
        errors.addAll(checkHtmlStructure(code));

        return errors;
    }

    /**
     * Validates HTML tag balancing: detects unclosed tags, mismatched closing tags,
     * and unexpected closing tags with character-level offsets.
     */
    private static List<SyntaxError> checkHtmlTagBalance(String code) {
        List<SyntaxError> errors = new ArrayList<>();
        Deque<int[]> stack = new ArrayDeque<>(); // [tagNameHash, line, startOffset, endOffset]
        Deque<String> nameStack = new ArrayDeque<>();

        Matcher m = HTML_TAG_PATTERN.matcher(code);
        while (m.find()) {
            boolean isClosing = !m.group(1).isEmpty();
            String tagName = m.group(2).toLowerCase();
            boolean isSelfClosing = !m.group(4).isEmpty();

            if (VOID_ELEMENTS.contains(tagName) || isSelfClosing) continue;

            int pos = m.start();
            int line = 1;
            int col = 1;
            for (int i = 0; i < pos && i < code.length(); i++) {
                if (code.charAt(i) == '\n') { line++; col = 1; } else { col++; }
            }

            if (isClosing) {
                if (nameStack.isEmpty()) {
                    errors.add(new SyntaxError(line, col,
                            "Unexpected closing tag: </" + tagName + ">",
                            SyntaxError.Severity.ERROR, m.start(), m.end()));
                } else if (nameStack.peek().equals(tagName)) {
                    nameStack.pop();
                    stack.pop();
                } else {
                    // Try to find matching tag deeper in stack
                    boolean found = false;
                    for (String name : nameStack) {
                        if (name.equals(tagName)) { found = true; break; }
                    }
                    if (found) {
                        // Pop until we find the match, reporting unclosed tags
                        while (!nameStack.isEmpty() && !nameStack.peek().equals(tagName)) {
                            String unclosed = nameStack.pop();
                            int[] info = stack.pop();
                            errors.add(new SyntaxError(info[1], 1,
                                    "Unclosed tag: <" + unclosed + ">",
                                    SyntaxError.Severity.ERROR, info[2], info[3]));
                        }
                        if (!nameStack.isEmpty()) { nameStack.pop(); stack.pop(); }
                    } else {
                        errors.add(new SyntaxError(line, col,
                                "Mismatched closing tag: expected </" + nameStack.peek()
                                        + "> but found </" + tagName + ">",
                                SyntaxError.Severity.ERROR, m.start(), m.end()));
                    }
                }
            } else {
                nameStack.push(tagName);
                stack.push(new int[]{tagName.hashCode(), line, m.start(), m.end()});
            }
        }

        // Remaining unclosed tags
        while (!nameStack.isEmpty()) {
            String unclosed = nameStack.pop();
            int[] info = stack.pop();
            errors.add(new SyntaxError(info[1], 1,
                    "Unclosed tag: <" + unclosed + ">",
                    SyntaxError.Severity.WARNING, info[2], info[3]));
        }

        return errors;
    }

    /**
     * Checks HTML structural issues:
     * - Missing doctype
     * - Missing <html>, <head>, <body> elements
     * - Duplicate IDs
     * - Duplicate attributes within a tag
     */
    private static List<SyntaxError> checkHtmlStructure(String code) {
        List<SyntaxError> errors = new ArrayList<>();
        String lower = code.toLowerCase();

        // Check for DOCTYPE
        if (!lower.trim().startsWith("<!doctype")) {
            errors.add(new SyntaxError(1, 1,
                    "Missing <!DOCTYPE html> declaration",
                    SyntaxError.Severity.WARNING, 0, Math.min(1, code.length())));
        }

        // Check required structural elements (only if code looks like a full HTML doc)
        if (lower.contains("<html") || lower.contains("<head") || lower.contains("<body")) {
            if (!lower.contains("<html")) {
                errors.add(new SyntaxError(1, 1, "Missing <html> element",
                        SyntaxError.Severity.WARNING));
            }
            if (!lower.contains("<head")) {
                errors.add(new SyntaxError(1, 1, "Missing <head> element",
                        SyntaxError.Severity.WARNING));
            }
            if (!lower.contains("<body")) {
                errors.add(new SyntaxError(1, 1, "Missing <body> element",
                        SyntaxError.Severity.WARNING));
            }
        }

        // Check for duplicate attributes within tags
        Pattern tagWithAttrs = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)\\s+([^>]+)>");
        Matcher tm = tagWithAttrs.matcher(code);
        while (tm.find()) {
            String attrs = tm.group(2);
            Pattern attrName = Pattern.compile("([a-zA-Z][a-zA-Z0-9-]*)\\s*=");
            Matcher am = attrName.matcher(attrs);
            Set<String> seen = new HashSet<>();
            while (am.find()) {
                String name = am.group(1).toLowerCase();
                if (!seen.add(name)) {
                    int pos = tm.start() + tm.group(1).length() + 1 + am.start();
                    int line = 1;
                    for (int i = 0; i < pos && i < code.length(); i++) {
                        if (code.charAt(i) == '\n') line++;
                    }
                    errors.add(new SyntaxError(line, 1,
                            "Duplicate attribute '" + name + "' in <" + tm.group(1) + ">",
                            SyntaxError.Severity.WARNING,
                            pos, Math.min(pos + am.group(1).length(), code.length())));
                }
            }
        }

        // Check for duplicate IDs
        Pattern idPattern = Pattern.compile("id\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher idm = idPattern.matcher(code);
        Map<String, Integer> idFirstLine = new HashMap<>();
        while (idm.find()) {
            String id = idm.group(1);
            int pos = idm.start();
            int line = 1;
            for (int i = 0; i < pos && i < code.length(); i++) {
                if (code.charAt(i) == '\n') line++;
            }
            if (idFirstLine.containsKey(id)) {
                errors.add(new SyntaxError(line, 1,
                        "Duplicate id '" + id + "' (first seen on line " + idFirstLine.get(id) + ")",
                        SyntaxError.Severity.ERROR,
                        idm.start(), idm.end()));
            } else {
                idFirstLine.put(id, line);
            }
        }

        return errors;
    }

    /**
     * Shuts down the checker executor.
     */
    public static void shutdown() {
        CHECKER_EXECUTOR.shutdownNow();
    }
}
