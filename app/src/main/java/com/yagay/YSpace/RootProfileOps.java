package com.yagay.YSpace;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

final class RootProfileOps {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_.$]+");

    static final class Result {
        final boolean ok;
        final int code;
        final String output;

        Result(boolean ok, int code, String output) {
            this.ok = ok;
            this.code = code;
            this.output = output == null ? "" : output.trim();
        }
    }

    private RootProfileOps() {}

    static Result hasRoot() {
        return run("id");
    }

    static Result clonePackage(String packageName, int userId) {
        if (!valid(packageName)) return new Result(false, -1, "Invalid package name");
        return run("cmd package install-existing --user " + userId + " " + packageName);
    }

    static Result removePackage(String packageName, int userId) {
        if (!valid(packageName)) return new Result(false, -1, "Invalid package name");
        return run("pm uninstall --user " + userId + " " + packageName);
    }

    private static boolean valid(String packageName) {
        return packageName != null && PACKAGE.matcher(packageName).matches();
    }

    static Result run(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) out.append(line).append('\n');
            }
            int code = process.waitFor();
            return new Result(code == 0, code, out.toString());
        } catch (Throwable t) {
            return new Result(false, -1, t.getClass().getSimpleName() + ": " + t.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }
}
