package com.mathio.lms.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Truy vấn SQL Server cho module Configuration; dùng Connection của transaction đang xử lý. */
public class ConfigurationDAO {
    public Map<String, Object> getSettings(Connection connection) throws SQLException {
        String sql =
                """
                    SELECT *
                    FROM gemini_settings
                    WHERE singleton=1
                """
                        .strip();
        return Database.one(connection, sql);
    }

    public Map<String, Object> getSettingsForUpdate(Connection connection) throws SQLException {
        String sql =
                """
                    SELECT id
                    FROM gemini_settings WITH(UPDLOCK,HOLDLOCK)
                    WHERE singleton=1
                """
                        .strip();
        return Database.one(connection, sql);
    }

    public int createSettings(
            Connection connection,
            String model,
            double temperature,
            int maxOutputTokens,
            boolean enabled,
            String encryptedApiKey)
            throws SQLException {
        String sql =
                """
                    INSERT gemini_settings(model,temperature,max_output_tokens,enabled,encrypted_api_key)
                    VALUES(?,?,?,?,?)
                """
                        .strip();
        return Database.update(
                connection, sql, model, temperature, maxOutputTokens, enabled, encryptedApiKey);
    }

    public int updateSettings(
            Connection connection,
            String model,
            double temperature,
            int maxOutputTokens,
            boolean enabled,
            String encryptedApiKey)
            throws SQLException {
        String sql =
                """
                    UPDATE gemini_settings
                    SET model=?,temperature=?,max_output_tokens=?,enabled=?,encrypted_api_key=COALESCE(?,encrypted_api_key)
                    WHERE singleton=1
                """
                        .strip();
        return Database.update(
                connection, sql, model, temperature, maxOutputTokens, enabled, encryptedApiKey);
    }

    public int auditSettings(Connection connection, long actorId) throws SQLException {
        String sql =
                """
                    INSERT audit_logs(actor_id,action)
                    VALUES(?,'GEMINI_CONFIG_UPDATED')
                """
                        .strip();
        return Database.update(connection, sql, actorId);
    }

    public List<Map<String, Object>> getPrompts(Connection connection) throws SQLException {
        String sql =
                """
                    SELECT name,
                           content
                    FROM prompt_templates
                """
                        .strip();
        return Database.rows(connection, sql);
    }

    public int updatePrompt(Connection connection, String content, String name)
            throws SQLException {
        String sql =
                """
                    UPDATE prompt_templates
                    SET content=?
                    WHERE name=?
                """
                        .strip();
        return Database.update(connection, sql, content, name);
    }

    public int createPrompt(Connection connection, String name, String content)
            throws SQLException {
        String sql =
                """
                    INSERT prompt_templates(name,content)
                    VALUES(?,?)
                """
                        .strip();
        return Database.update(connection, sql, name, content);
    }

    public int auditPrompts(Connection connection, long actorId) throws SQLException {
        String sql =
                """
                    INSERT audit_logs(actor_id,action)
                    VALUES(?,'PROMPTS_UPDATED')
                """
                        .strip();
        return Database.update(connection, sql, actorId);
    }
}
