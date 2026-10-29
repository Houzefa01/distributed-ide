package com.distributed_ide.lsp_bridge.transport;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LSP stdio transport: Content-Length framed messages. */
public final class LspMessageTransport {

    private static final Pattern CONTENT_LENGTH = Pattern.compile("Content-Length:\\s*(\\d+)");
    private static final String HEADER = "Content-Length: ";

    private LspMessageTransport() {}

    public static String readMessage(InputStream in) throws IOException {
        StringBuilder header = new StringBuilder();
        int c;
        while (true) {
            c = in.read();
            if (c == -1) return null;
            header.append((char) c);
            if (header.length() >= 4 && "\r\n\r\n".equals(header.substring(header.length() - 4))) break;
        }
        Matcher m = CONTENT_LENGTH.matcher(header);
        if (!m.find()) throw new IOException("Missing Content-Length: " + header);
        int len = Integer.parseInt(m.group(1));
        byte[] body = new byte[len];
        int read = 0;
        while (read < len) {
            int n = in.read(body, read, len - read);
            if (n <= 0) throw new IOException("EOF");
            read += n;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    public static void writeMessage(OutputStream out, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        out.write((HEADER + bytes.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes);
        out.flush();
    }
}
