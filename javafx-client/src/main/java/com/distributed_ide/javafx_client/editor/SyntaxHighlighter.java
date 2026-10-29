package com.distributed_ide.javafx_client.editor;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Multi-language syntax highlighting engine.
 * Each language is defined by a set of patterns compiled once.
 * Thread-safe: patterns are immutable, computation is pure.
 */
public class SyntaxHighlighter {

    private static final Map<String, LanguagePattern> LANGUAGES = new HashMap<>();

    static {
        // FIX 2: Register all supported language tokenizers
        // Each language has a complete pattern set for keywords, strings, comments, etc.
        registerJava();
        registerPython();
        registerJavaScript();
        registerTypeScript();
        registerHtml();
        registerXml();
        registerCss();
        registerSql();
        registerShell();
        registerJson();
        registerMarkdown();
    }
    
    // FIX 2a: Add TypeScript support (more advanced than JavaScript)
    private static void registerTypeScript() {
        String keywords = String.join("|",
                "abstract", "any", "as", "async", "await", "boolean", "break",
                "case", "catch", "class", "const", "continue", "constructor",
                "debugger", "declare", "default", "delete", "do", "else",
                "enum", "export", "extends", "false", "finally", "for",
                "from", "function", "get", "global", "goto", "if", "import",
                "implements", "in", "instanceof", "interface", "is", "keyof",
                "let", "module", "namespace", "never", "new", "null", "number",
                "of", "package", "private", "protected", "public", "readonly",
                "require", "return", "set", "static", "string", "super",
                "switch", "symbol", "this", "throw", "true", "try", "type",
                "typeof", "unique", "var", "void", "while", "with", "yield"
        );
        LanguagePattern lang = new PatternBuilder()
                .add("KEYWORD", "\\b(" + keywords + ")\\b", "keyword")
                .add("GENERIC", "<[A-Z][A-Za-z0-9]*>", "annotation")
                .add("NUMBER", "\\b\\d+\\.?\\d*([eE][+-]?\\d+)?\\b", "number")
                .add("TEMPLATE", "`[^`]*`", "string")
                .add("STRING", "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'", "string")
                .add("BLOCKCOMMENT", "/\\*[\\s\\S]*?\\*/", "comment")
                .add("LINECOMMENT", "//[^\n]*", "comment")
                .add("ARROW", "=>", "keyword")
                .add("METHOD", "\\b[a-z_$][A-Za-z0-9_$]*(?=\\s*\\()", "method")
                .build();
        LANGUAGES.put("typescript", lang);
    }

    /**
     * Computes style spans for the given text according to the language.
     * Returns an empty collection if the language is unknown.
     * Optimized: single regex pass over the entire text.
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text, String language) {
        if (text == null || text.isEmpty()) {
            return emptySpans();
        }
        LanguagePattern lang = LANGUAGES.get(language != null ? language.toLowerCase() : "");
        if (lang == null) {
            return emptySpans();
        }
        return computeSpans(text, lang);
    }

    public static Set<String> getSupportedLanguages() {
        return Collections.unmodifiableSet(LANGUAGES.keySet());
    }

    public static boolean isSupported(String language) {
        return language != null && LANGUAGES.containsKey(language.toLowerCase());
    }

    // ═══════════════════════════════════════════════════════════
    //  CORE COMPUTATION
    // ═══════════════════════════════════════════════════════════

    private static StyleSpans<Collection<String>> computeSpans(String text, LanguagePattern lang) {
        Matcher matcher = lang.pattern.matcher(text);
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        int lastEnd = 0;

        while (matcher.find()) {
            String styleClass = null;
            for (int i = 0; i < lang.groupNames.size(); i++) {
                if (matcher.group(lang.groupNames.get(i)) != null) {
                    styleClass = lang.styleClasses.get(i);
                    break;
                }
            }
            if (styleClass == null) continue;

            int start = matcher.start();
            if (start > lastEnd) {
                builder.add(Collections.emptyList(), start - lastEnd);
            }
            builder.add(Collections.singleton(styleClass), matcher.end() - start);
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            builder.add(Collections.emptyList(), text.length() - lastEnd);
        }

        if (lastEnd == 0 && text.length() > 0) {
            builder.add(Collections.emptyList(), text.length());
        }

        return builder.create();
    }

    private static StyleSpans<Collection<String>> emptySpans() {
        StyleSpansBuilder<Collection<String>> b = new StyleSpansBuilder<>();
        b.add(Collections.emptyList(), 0);
        return b.create();
    }

    // ═══════════════════════════════════════════════════════════
    //  LANGUAGE PATTERN DEFINITION
    // ═══════════════════════════════════════════════════════════

    private static class LanguagePattern {
        final Pattern pattern;
        final List<String> groupNames;
        final List<String> styleClasses;

        LanguagePattern(Pattern pattern, List<String> groupNames, List<String> styleClasses) {
            this.pattern = pattern;
            this.groupNames = groupNames;
            this.styleClasses = styleClasses;
        }
    }

    private static class PatternBuilder {
        private final List<String> parts = new ArrayList<>();
        private final List<String> groupNames = new ArrayList<>();
        private final List<String> styleClasses = new ArrayList<>();

        PatternBuilder add(String groupName, String regex, String styleClass) {
            parts.add("(?<" + groupName + ">" + regex + ")");
            groupNames.add(groupName);
            styleClasses.add(styleClass);
            return this;
        }

        LanguagePattern build() {
            String combined = String.join("|", parts);
            return new LanguagePattern(
                    Pattern.compile(combined, Pattern.MULTILINE),
                    groupNames,
                    styleClasses
            );
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  LANGUAGE DEFINITIONS
    // ═══════════════════════════════════════════════════════════

    private static void registerJava() {
        String keywords = String.join("|",
                "abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double",
                "else", "enum", "extends", "final", "finally", "float", "for",
                "goto", "if", "implements", "import", "instanceof", "int",
                "interface", "long", "native", "new", "package", "private",
                "protected", "public", "return", "short", "static", "strictfp",
                "super", "switch", "synchronized", "this", "throw", "throws",
                "transient", "try", "void", "volatile", "while", "var", "yield",
                "record", "sealed", "permits", "non-sealed"
        );
        String primitiveTypes = "boolean|byte|char|short|int|long|float|double|void";
        LanguagePattern lang = new PatternBuilder()
                .add("BLOCKCOMMENT", "/\\*[\\s\\S]*?\\*/", "comment")
                .add("LINECOMMENT", "//[^\n]*", "comment")
                .add("ANNOTATION", "@[A-Za-z_][A-Za-z0-9_.]*", "annotation")
                .add("STRING", "\"\"\"[\\s\\S]*?\"\"\"|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'", "string")
                .add("NUMBER", "\\b\\d+[.dDfFlL]?\\d*\\b|0[xX][0-9a-fA-F]+\\b", "number")
                .add("KEYWORD", "\\b(" + keywords + ")\\b", "keyword")
                .add("PRIMITIVE", "\\b(" + primitiveTypes + ")\\b", "type")
                .add("CLASSNAME", "\\b[A-Z][A-Za-z0-9_]*\\b", "classname")
                .add("METHOD", "\\b[a-z_][A-Za-z0-9_]*(?=\\s*\\()", "method")
                .build();
        LANGUAGES.put("java", lang);
    }

    private static void registerPython() {
        String keywords = String.join("|",
                "and", "as", "assert", "async", "await", "break", "class", "continue",
                "def", "del", "elif", "else", "except", "finally", "for", "from",
                "global", "if", "import", "in", "is", "lambda", "nonlocal", "not",
                "or", "pass", "raise", "return", "try", "while", "with", "yield",
                "True", "False", "None"
        );
        String builtins = String.join("|",
                "print", "len", "range", "int", "str", "float", "list", "dict", "set",
                "tuple", "type", "isinstance", "issubclass", "super", "self", "cls",
                "abs", "all", "any", "bin", "bool", "bytearray", "bytes", "callable",
                "chr", "classmethod", "compile", "complex", "delattr", "dir", "divmod",
                "enumerate", "eval", "exec", "filter", "format", "frozenset", "getattr",
                "globals", "hasattr", "hash", "help", "hex", "id", "input", "iter",
                "locals", "map", "max", "min", "next", "object", "oct", "open", "ord",
                "pow", "property", "repr", "reversed", "round", "setattr", "slice",
                "sorted", "staticmethod", "sum", "vars", "zip", "__init__", "__name__",
                "__main__", "__all__", "__doc__", "__file__"
        );
        LanguagePattern lang = new PatternBuilder()
                .add("TRIPLESTRING", "\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''", "string")
                .add("COMMENT", "#[^\n]*", "comment")
                .add("DECORATOR", "@[A-Za-z_][A-Za-z0-9_.]*", "annotation")
                .add("STRING", "f\"([^\"\\\\]|\\\\.)*\"|f'([^'\\\\]|\\\\.)*'|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'", "string")
                .add("NUMBER", "\\b\\d+\\.?\\d*([eE][+-]?\\d+)?[jJ]?\\b|0[xXoObB][0-9a-fA-F_]+\\b", "number")
                .add("KEYWORD", "\\b(" + keywords + ")\\b", "keyword")
                .add("BUILTIN", "\\b(" + builtins + ")\\b", "type")
                .add("CLASSNAME", "(?<=\\bclass\\s)[A-Z][A-Za-z0-9_]*|\\b[A-Z][A-Za-z0-9_]*(?=\\s*\\()", "classname")
                .add("METHOD", "(?<=\\bdef\\s)[a-z_][A-Za-z0-9_]*|\\b[a-z_][A-Za-z0-9_]*(?=\\s*\\()", "method")
                .build();
        LANGUAGES.put("python", lang);
    }

    private static void registerJavaScript() {
        String keywords = String.join("|",
                "async", "await", "break", "case", "catch", "class", "const", "continue",
                "debugger", "default", "delete", "do", "else", "export", "extends",
                "finally", "for", "from", "function", "if", "import", "in", "instanceof",
                "let", "new", "of", "return", "static", "super", "switch", "this",
                "throw", "try", "typeof", "var", "void", "while", "with", "yield",
                "true", "false", "null", "undefined", "NaN", "Infinity"
        );
        LanguagePattern lang = new PatternBuilder()
                .add("KEYWORD", "\\b(" + keywords + ")\\b", "keyword")
                .add("NUMBER", "\\b\\d+\\.?\\d*([eE][+-]?\\d+)?\\b", "number")
                .add("TEMPLATE", "`[^`]*`", "string")
                .add("STRING", "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'", "string")
                .add("BLOCKCOMMENT", "/\\*[\\s\\S]*?\\*/", "comment")
                .add("LINECOMMENT", "//[^\n]*", "comment")
                .add("ARROW", "=>", "keyword")
                .add("METHOD", "\\b[a-z_$][A-Za-z0-9_$]*(?=\\s*\\()", "method")
                .build();
        LANGUAGES.put("javascript", lang);
        LANGUAGES.put("typescript", lang);
    }

    private static void registerXml() {
        LanguagePattern lang = new PatternBuilder()
                .add("XMLCOMMENT", "<!--[\\s\\S]*?-->", "comment")
                .add("CDATA", "<!\\[CDATA\\[[\\s\\S]*?\\]\\]>", "string")
                .add("PROLOG", "<\\?[\\s\\S]*?\\?>", "annotation")
                .add("TAG", "</?[A-Za-z_:][A-Za-z0-9_.:-]*", "keyword")
                .add("CLOSETAG", "/?>", "keyword")
                .add("ATTR", "\\b[A-Za-z_:][A-Za-z0-9_.:-]*(?=\\s*=)", "type")
                .add("ATTRVAL", "\"[^\"]*\"|'[^']*'", "string")
                .build();
        LANGUAGES.put("xml", lang);
    }

    private static void registerHtml() {
        LanguagePattern lang = new PatternBuilder()
                .add("HTMLCOMMENT", "<!--[\\s\\S]*?-->", "comment")
                .add("DOCTYPE", "<!DOCTYPE[^>]*>", "annotation")
                .add("SCRIPT", "<script[\\s\\S]*?</script>", "string")
                .add("STYLE", "<style[\\s\\S]*?</style>", "string")
                .add("TAG", "</?[A-Za-z][A-Za-z0-9]*", "keyword")
                .add("CLOSETAG", "/?>", "keyword")
                .add("ATTR", "\\b[a-zA-Z_-][a-zA-Z0-9_-]*(?=\\s*=)", "classname")
                .add("ATTRVAL", "\"[^\"]*\"|'[^']*'", "string")
                .add("ENTITY", "&[a-zA-Z]+;|&#\\d+;|&#x[0-9a-fA-F]+;", "number")
                .build();
        LANGUAGES.put("html", lang);
        LANGUAGES.put("htm", lang);
    }

    private static void registerCss() {
        LanguagePattern lang = new PatternBuilder()
                .add("COMMENT", "/\\*[\\s\\S]*?\\*/", "comment")
                .add("SELECTOR", "[.#]?[A-Za-z_-][A-Za-z0-9_-]*(?=\\s*[{,:])", "keyword")
                .add("PROPERTY", "[a-z-]+(?=\\s*:)", "type")
                .add("VALUE", "#[0-9A-Fa-f]{3,8}\\b|\\b\\d+(\\.\\d+)?(px|em|rem|%|vh|vw|s|ms)\\b", "number")
                .add("STRING", "\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)*'", "string")
                .build();
        LANGUAGES.put("css", lang);
    }

    private static void registerSql() {
        String keywords = String.join("|",
                "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "INSERT", "INTO", "VALUES",
                "UPDATE", "SET", "DELETE", "CREATE", "TABLE", "DROP", "ALTER", "INDEX",
                "JOIN", "LEFT", "RIGHT", "INNER", "OUTER", "ON", "AS", "ORDER", "BY",
                "GROUP", "HAVING", "LIMIT", "OFFSET", "UNION", "ALL", "DISTINCT",
                "NULL", "IS", "IN", "BETWEEN", "LIKE", "EXISTS", "CASE", "WHEN", "THEN",
                "ELSE", "END", "COUNT", "SUM", "AVG", "MIN", "MAX", "PRIMARY", "KEY",
                "FOREIGN", "REFERENCES", "CONSTRAINT", "DEFAULT", "NOT", "AUTO_INCREMENT",
                "select", "from", "where", "and", "or", "not", "insert", "into", "values",
                "update", "set", "delete", "create", "table", "drop", "alter", "index",
                "join", "left", "right", "inner", "outer", "on", "as", "order", "by",
                "group", "having", "limit", "offset", "union", "all", "distinct",
                "null", "is", "in", "between", "like", "exists", "case", "when", "then",
                "else", "end"
        );
        LanguagePattern lang = new PatternBuilder()
                .add("KEYWORD", "\\b(" + keywords + ")\\b", "keyword")
                .add("NUMBER", "\\b\\d+\\.?\\d*\\b", "number")
                .add("STRING", "'([^'\\\\]|\\\\.)*'|\"([^\"\\\\]|\\\\.)*\"", "string")
                .add("LINECOMMENT", "--[^\n]*", "comment")
                .add("BLOCKCOMMENT", "/\\*[\\s\\S]*?\\*/", "comment")
                .build();
        LANGUAGES.put("sql", lang);
    }

    private static void registerShell() {
        String keywords = String.join("|",
                "if", "then", "else", "elif", "fi", "for", "while", "do", "done",
                "case", "esac", "in", "function", "return", "local", "export",
                "echo", "read", "exit", "source", "set", "unset", "shift"
        );
        LanguagePattern lang = new PatternBuilder()
                .add("KEYWORD", "\\b(" + keywords + ")\\b", "keyword")
                .add("VARIABLE", "\\$[A-Za-z_][A-Za-z0-9_]*|\\$\\{[^}]+\\}", "annotation")
                .add("NUMBER", "\\b\\d+\\b", "number")
                .add("STRING", "\"([^\"\\\\]|\\\\.)*\"|'[^']*'", "string")
                .add("COMMENT", "#[^\n]*", "comment")
                .build();
        LANGUAGES.put("shell", lang);
        LANGUAGES.put("bash", lang);
    }

    private static void registerJson() {
        LanguagePattern lang = new PatternBuilder()
                .add("KEY", "\"[^\"]*\"(?=\\s*:)", "type")
                .add("STRING", "\"([^\"\\\\]|\\\\.)*\"", "string")
                .add("NUMBER", "-?\\b\\d+\\.?\\d*([eE][+-]?\\d+)?\\b", "number")
                .add("BOOLEAN", "\\b(true|false|null)\\b", "keyword")
                .build();
        LANGUAGES.put("json", lang);
    }

    private static void registerMarkdown() {
        LanguagePattern lang = new PatternBuilder()
                .add("HEADING", "^#{1,6}\\s+.+$", "keyword")
                .add("BOLD", "\\*\\*[^*]+\\*\\*|__[^_]+__", "type")
                .add("ITALIC", "\\*[^*]+\\*|_[^_]+_", "annotation")
                .add("CODE", "`[^`]+`", "string")
                .add("CODEBLOCK", "```[\\s\\S]*?```", "string")
                .add("LINK", "\\[[^]]+\\]\\([^)]+\\)", "method")
                .build();
        LANGUAGES.put("markdown", lang);
    }
}
