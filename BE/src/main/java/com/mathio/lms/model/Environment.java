package com.mathio.lms.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Environment {
    private static volatile Map<String, String> local = Map.of();

    /** Đọc cấu hình riêng ngoài WAR khi chạy Tomcat từ NetBeans. */
    public static void load(Path file) {
        if (file == null) {
            local = Map.of();
            return;
        }
        Path configFile = file.toAbsolutePath().normalize();
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(configFile, StandardCharsets.UTF_8)) {
                line = line.replace("\uFEFF", "").strip();
                int separator = line.indexOf('=');
                if (separator < 1) continue;
                String key = line.substring(0, separator).strip();
                if (!key.matches("[A-Z][A-Z0-9_]*")) continue;
                String value = line.substring(separator + 1).strip();
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                                || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read backend configuration: " + configFile, e);
        }
        Path storage = Path.of(values.getOrDefault("STORAGE_DIR", "private-storage"));
        if (!storage.isAbsolute()) storage = configFile.getParent().resolve(storage);
        values.put("STORAGE_DIR", storage.normalize().toString());
        local = Map.copyOf(values);
    }

    public static String get(String key, String fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) value = System.getenv(key);
        if (value == null || value.isBlank()) value = local.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static boolean production() {
        return get("APP_ENV", "development").equals("production");
    }

    public static int integer(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(get(key, Integer.toString(fallback)));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid numeric configuration: " + key);
        }
    }

    private Environment() {}
}
