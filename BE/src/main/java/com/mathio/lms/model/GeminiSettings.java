package com.mathio.lms.model;

import java.util.Map;

public class GeminiSettings {
    private String model;
    private double temperature;
    private int maxOutputTokens;
    private boolean enabled;
    private String key;

    public GeminiSettings() {}

    public GeminiSettings(
            String model, double temperature, int maxOutputTokens, boolean enabled, String key) {
        this.model = model;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.enabled = enabled;
        this.key = key;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Map<String, Object> publicView() {
        return Map.of(
                "model",
                model,
                "temperature",
                temperature,
                "maxOutputTokens",
                maxOutputTokens,
                "enabled",
                enabled,
                "hasApiKey",
                !key.isBlank());
    }
}
