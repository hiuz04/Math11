package com.mathio.lms.model;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

public class ImageStorage {
    public record Image(byte[] bytes, String mime, String hash) {}

    public static Image decode(String data) {
        ApiException.require(
                data != null && data.length() <= 11_185_000, 413, "Ảnh vượt quá 8 MB.");
        ApiException.require(
                data.matches("(?s)^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\r\\n]+$"),
                400,
                "Chỉ nhận ảnh JPEG, PNG hoặc WEBP.");
        try {
            int comma = data.indexOf(',');
            String mime = data.substring(5, data.indexOf(';'));
            byte[] bytes = Base64.getMimeDecoder().decode(data.substring(comma + 1));
            ApiException.require(
                    bytes.length > 0 && bytes.length <= 8 * 1024 * 1024, 413, "Ảnh vượt quá 8 MB.");
            try (var inputStream =
                    new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(inputStream);
                ApiException.require(readers.hasNext(), 400, "Không đọc được nội dung ảnh.");
                var reader = readers.next();
                try {
                    reader.setInput(inputStream);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    ApiException.require(
                            (mime.equals("image/jpeg")
                                            && (format.equals("jpeg") || format.equals("jpg")))
                                    || mime.equals("image/" + format),
                            400,
                            "Định dạng ảnh không khớp nội dung.");
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    ApiException.require(
                            width > 0 && height > 0 && (long) width * height <= 30_000_000,
                            400,
                            "Kích thước ảnh không hợp lệ.");
                    ApiException.require(reader.read(0) != null, 400, "Ảnh bị hỏng.");
                } finally {
                    reader.dispose();
                }
            }
            return new Image(
                    bytes,
                    mime,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(400, "Ảnh bị hỏng hoặc không hợp lệ.");
        }
    }

    public String store(Image image) {
        try {
            Path root =
                    Path.of(Environment.get("STORAGE_DIR", "private-storage"))
                            .toAbsolutePath()
                            .normalize();
            Files.createDirectories(root);
            String key = UUID.randomUUID().toString();
            Files.write(root.resolve(key), image.bytes(), StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Image storage unavailable", e);
        }
    }

    public byte[] read(String key) {
        ApiException.require(key.matches("[a-f0-9-]{36}"), 404, "Không tìm thấy ảnh.");
        try {
            return Files.readAllBytes(
                    Path.of(Environment.get("STORAGE_DIR", "private-storage"))
                            .toAbsolutePath()
                            .normalize()
                            .resolve(key));
        } catch (IOException e) {
            throw new ApiException(404, "Không tìm thấy ảnh.");
        }
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(
                    Path.of(Environment.get("STORAGE_DIR", "private-storage"))
                            .toAbsolutePath()
                            .normalize()
                            .resolve(key));
        } catch (IOException ignored) {
        }
    }
}
