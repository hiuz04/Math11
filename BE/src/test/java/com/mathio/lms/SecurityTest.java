package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.ImageStorage;
import com.mathio.lms.model.MathAnswer;
import com.mathio.lms.model.RichText;
import com.mathio.lms.model.Secrets;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

class SecurityTest {
    @TempDir Path dir;

    @AfterEach
    void reset() {
        System.clearProperty("STORAGE_DIR");
        System.clearProperty("APP_ENCRYPTION_KEY");
    }

    @Test
    void roleChecksCannotBeOverridden() {
        Actor s = new Actor(1, "STUDENT", 1);
        assertThrows(ApiException.class, s::requireStaff);
        assertThrows(ApiException.class, s::requireAdmin);
        assertDoesNotThrow(new Actor(2, "TEACHER", 1)::requireStaff);
        assertThrows(ApiException.class, new Actor(2, "TEACHER", 1)::requireAdmin);
        assertDoesNotThrow(new Actor(3, "ADMIN", 1)::requireAdmin);
    }

    @Test
    void htmlSanitizationKeepsMathAndRemovesExecutableContent() {
        String result =
                RichText.clean(
                        "<h2>Toán</h2><span data-type='inline-math' data-latex='x^2'"
                                + " onclick='alert(1)'></span><script>alert(1)</script><a"
                                + " href='javascript:alert(1)'>X</a><img src=x onerror=alert(1)>");
        assertTrue(result.contains("data-latex"));
        assertTrue(result.contains("h2"));
        assertFalse(result.contains("script"));
        assertFalse(result.contains("onclick"));
        assertFalse(result.contains("onerror"));
        assertFalse(result.contains("javascript:"));
    }

    @Test
    void encryptedKeysUseRandomIvAndRejectTampering() {
        System.setProperty("APP_ENCRYPTION_KEY", Base64.getEncoder().encodeToString(new byte[32]));
        String first = Secrets.encrypt("secret-value"), second = Secrets.encrypt("secret-value");
        assertNotEquals(first, second);
        assertFalse(first.contains("secret-value"));
        assertEquals("secret-value", Secrets.decrypt(first));
        String bad =
                first.substring(0, first.indexOf(':') + 1)
                        + Base64.getEncoder().encodeToString(new byte[40]);
        assertThrows(ApiException.class, () -> Secrets.decrypt(bad));
    }

    @Test
    void savingKeyRequiresIndependentEncryptionKey() {
        assertThrows(ApiException.class, () -> Secrets.encrypt("secret"));
    }

    static String png() throws Exception {
        BufferedImage image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xff123456);
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(image, "png", b);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(b.toByteArray());
    }

    @Test
    void imageDecodeHashAndPrivateStorageAreReal() throws Exception {
        System.setProperty("STORAGE_DIR", dir.toString());
        var first = ImageStorage.decode(png());
        var renamed = ImageStorage.decode(png());
        assertEquals(first.hash(), renamed.hash());
        assertEquals(64, first.hash().length());
        ImageStorage storage = new ImageStorage();
        String key = storage.store(first);
        assertArrayEquals(first.bytes(), storage.read(key));
        assertThrows(ApiException.class, () -> storage.read("../../secret"));
        storage.delete(key);
        assertThrows(ApiException.class, () -> storage.read(key));
    }

    @Test
    void imageMimeSpoofCorruptionAndSizeAreRejected() throws Exception {
        assertThrows(
                ApiException.class,
                () -> ImageStorage.decode(png().replace("image/png", "image/jpeg")));
        assertThrows(
                ApiException.class, () -> ImageStorage.decode("data:image/png;base64,SGVsbG8="));
        assertEquals(
                413,
                assertThrows(ApiException.class, () -> ImageStorage.decode("x".repeat(11_185_001)))
                        .status);
    }

    @Test
    void mathChecksEquivalentArithmeticButNeverExecutesCode() {
        assertTrue(MathAnswer.matches("2 + (10 − 1) × 3", "29"));
        assertTrue(MathAnswer.matches("3*2^(5-1)", "48"));
        assertTrue(MathAnswer.matches("x + 1", "x+1"));
        assertTrue(MathAnswer.matches("-2^2", "-4"));
        assertFalse(MathAnswer.matches("-2^2", "4"));
        assertTrue(MathAnswer.matches("2^-1", "0.5"));
        assertFalse(MathAnswer.matches("30", "29"));
        assertFalse(MathAnswer.matches("java.lang.Runtime.exec('x')", "29"));
        assertFalse(MathAnswer.matches("1/0", "0"));
        assertFalse(MathAnswer.matches("2^999999", "1"));
    }
}
