package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.mathio.lms.model.Environment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

class EnvironmentTest {
    @TempDir Path dir;

    @AfterEach
    void reset() {
        Environment.load(null);
        System.clearProperty("MATHIO_TEST_SETTING");
    }

    @Test
    void readsUtf8QuotedValuesAndPreservesEqualsWithoutExecutingAnything() throws Exception {
        Path file = dir.resolve(".env");
        Files.writeString(
                file,
                "\uFEFF# Local settings\nMATHIO_TEST_SETTING='Tiếng Việt=x'\n"
                        + "MATHIO_TEST_LITERAL=\"$(no-command)\"\nexport INVALID=ignored\n");
        Environment.load(file);
        assertEquals("Tiếng Việt=x", Environment.get("MATHIO_TEST_SETTING", "fallback"));
        assertEquals("$(no-command)", Environment.get("MATHIO_TEST_LITERAL", "fallback"));
        assertEquals("fallback", Environment.get("INVALID", "fallback"));
        assertEquals(dir.resolve("private-storage").toString(), Environment.get("STORAGE_DIR", ""));
    }

    @Test
    void systemPropertiesOverrideLocalFileAndBlankValuesUseFallback() throws Exception {
        Path file = dir.resolve(".env");
        Files.writeString(file, "MATHIO_TEST_SETTING=file\nMATHIO_TEST_EMPTY=\n");
        Environment.load(file);
        System.setProperty("MATHIO_TEST_SETTING", "system");
        assertEquals("system", Environment.get("MATHIO_TEST_SETTING", "fallback"));
        assertEquals("fallback", Environment.get("MATHIO_TEST_EMPTY", "fallback"));
    }

    @Test
    void storageIsRelativeToConfigurationInsteadOfTomcatWorkingDirectory() throws Exception {
        Path file = dir.resolve(".env");
        Files.writeString(file, "STORAGE_DIR=./uploads\n");
        Environment.load(file);
        assertEquals(dir.resolve("uploads").toString(), Environment.get("STORAGE_DIR", ""));
    }

    @Test
    void missingExplicitConfigurationFailsClearly() {
        assertThrows(
                IllegalStateException.class, () -> Environment.load(dir.resolve("missing.env")));
    }
}
