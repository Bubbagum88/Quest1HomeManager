package io.github.bubbagum88.homemanager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Runs one command through Magisk's su and returns its exit code and output. */
final class Root {
    static final class Result {
        final int code;
        final String out;
        Result(int code, String out) { this.code = code; this.out = out; }
        boolean ok() { return code == 0; }
    }

    static Result run(String cmd) {
        try {
            Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            String out = read(p.getInputStream());
            return new Result(p.waitFor(), out.trim());
        } catch (Exception e) {
            return new Result(-1, "su failed: " + e);
        }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    /** Single-quotes a value for the shell. Package names never contain a quote. */
    static String q(String s) { return "'" + s.replace("'", "") + "'"; }

    private Root() {}
}
