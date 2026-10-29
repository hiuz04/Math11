package com.mathio.lms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.DAO.ConfigurationDAO;
import com.mathio.lms.DAO.Database;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigurationService {
    private final Database database;
    private final ConfigurationDAO configurationDAO = new ConfigurationDAO();

    public ConfigurationService(Database database) {
        this.database = database;
    }

    public GeminiSettings getSettings() {
        return database.read(
                connection -> {
                    var row = configurationDAO.getSettings(connection);
                    String key = Environment.get("GEMINI_API_KEY", "");
                    if (row == null)
                        return new GeminiSettings(
                                Environment.get("GEMINI_MODEL", "gemini-2.5-flash"),
                                0.2,
                                4096,
                                !key.isBlank(),
                                key);
                    if (row.get("encrypted_api_key") != null)
                        key = Secrets.decrypt(row.get("encrypted_api_key").toString());
                    return new GeminiSettings(
                            row.get("model").toString(),
                            ((Number) row.get("temperature")).doubleValue(),
                            ((Number) row.get("max_output_tokens")).intValue(),
                            Boolean.TRUE.equals(row.get("enabled")),
                            key);
                });
    }

    public GeminiSettings validateSettings(JsonNode input, String existingKey) {
        String model = Json.text(input, "model", 100);
        double temperature = input.path("temperature").asDouble(-1);
        int maxOutputTokens = input.path("maxOutputTokens").asInt();
        ApiException.require(
                model.matches("[a-zA-Z0-9._-]+")
                        && input.path("temperature").isNumber()
                        && input.path("maxOutputTokens").isIntegralNumber()
                        && input.path("enabled").isBoolean()
                        && Double.isFinite(temperature)
                        && temperature >= 0
                        && temperature <= 2
                        && maxOutputTokens > 0
                        && maxOutputTokens <= 65536,
                400,
                "Cấu hình Gemini không hợp lệ.");
        String supplied = input.path("apiKey").asText("").trim();
        String key = supplied.isBlank() ? existingKey : supplied;
        ApiException.require(key.length() <= 1000, 400, "API key quá dài.");
        return new GeminiSettings(
                model, temperature, maxOutputTokens, input.path("enabled").asBoolean(), key);
    }

    public Object getConfiguration(Actor currentUser) {
        currentUser.requireAdmin();
        return getSettings().publicView();
    }

    public Object saveConfiguration(Actor currentUser, JsonNode input) {
        currentUser.requireAdmin();
        GeminiSettings settings = validateSettings(input, getSettings().getKey());
        String encrypted =
                input.hasNonNull("apiKey") && !input.path("apiKey").asText().isBlank()
                        ? Secrets.encrypt(settings.getKey())
                        : null;
        database.transaction(
                connection -> {
                    var old = configurationDAO.getSettingsForUpdate(connection);
                    if (old == null)
                        configurationDAO.createSettings(
                                connection,
                                settings.getModel(),
                                settings.getTemperature(),
                                settings.getMaxOutputTokens(),
                                settings.getEnabled(),
                                encrypted);
                    else
                        configurationDAO.updateSettings(
                                connection,
                                settings.getModel(),
                                settings.getTemperature(),
                                settings.getMaxOutputTokens(),
                                settings.getEnabled(),
                                encrypted);
                    configurationDAO.auditSettings(connection, currentUser.getId());
                    return null;
                });
        return settings.publicView();
    }

    public Map<String, String> getPromptTemplates() {
        return database.read(
                connection -> {
                    Map<String, String> out = new LinkedHashMap<>();
                    for (var row : configurationDAO.getPrompts(connection))
                        out.put(row.get("name").toString(), row.get("content").toString());
                    ApiException.require(
                            out.keySet()
                                    .containsAll(
                                            List.of(
                                                    "system",
                                                    "essayEvaluation",
                                                    "stepTutor",
                                                    "retryEvaluation")),
                            503,
                            "Chưa cài đặt prompt templates; hãy chạy seed.");
                    return out;
                });
    }

    public Object getPrompts(Actor currentUser) {
        currentUser.requireAdmin();
        return getPromptTemplates();
    }

    public void savePrompts(Actor currentUser, JsonNode input) {
        currentUser.requireAdmin();
        database.transaction(
                connection -> {
                    for (String k :
                            List.of("system", "essayEvaluation", "stepTutor", "retryEvaluation")) {
                        String value = Json.text(input, k, 30000);
                        if (configurationDAO.updatePrompt(connection, value, k) == 0)
                            configurationDAO.createPrompt(connection, k, value);
                    }
                    configurationDAO.auditPrompts(connection, currentUser.getId());
                    return null;
                });
    }
}
