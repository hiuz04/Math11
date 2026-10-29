package com.mathio.lms.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** Truy vấn SQL Server cho module Ai; dùng Connection của transaction đang xử lý. */
public class AiDAO {
    public int createRequestLog(
            Connection connection,
            long actorId,
            String purpose,
            String model,
            boolean success,
            long durationMs)
            throws SQLException {
        String sql =
                """
                    INSERT ai_request_logs(actor_id,purpose,model,success,duration_ms)
                    VALUES(?,?,?,?,?)
                """
                        .strip();
        return Database.update(connection, sql, actorId, purpose, model, success, durationMs);
    }

    public Map<String, Object> getTopic(Connection connection, String chapter, String topic)
            throws SQLException {
        String sql =
                """
                    SELECT t.id
                    FROM topics t
                    JOIN chapters ch ON ch.id=t.chapter_id
                    WHERE ch.title=? AND t.title=?
                """
                        .strip();
        return Database.one(connection, sql, chapter, topic);
    }

    public Map<String, Object> getOwnedConversation(
            Connection connection, long conversationId, long ownerId) throws SQLException {
        String sql =
                """
                    SELECT id
                    FROM ai_conversations
                    WHERE id=? AND owner_id=?
                """
                        .strip();
        return Database.one(connection, sql, conversationId, ownerId);
    }

    public List<Map<String, Object>> getRecentMessages(Connection connection, long conversationId)
            throws SQLException {
        String sql =
                """
                    SELECT TOP (20) role,
                           content
                    FROM ai_messages
                    WHERE conversation_id=?
                    ORDER BY id DESC
                """
                        .strip();
        return Database.rows(connection, sql, conversationId);
    }

    public long createConversation(Connection connection, long ownerId) throws SQLException {
        String sql =
                """
                    INSERT ai_conversations(owner_id)
                    VALUES(?)
                """
                        .strip();
        return Database.insert(connection, sql, ownerId);
    }

    public int createUserMessage(Connection connection, long conversationId, String content)
            throws SQLException {
        String sql =
                """
                    INSERT ai_messages(conversation_id,role,content)
                    VALUES(?,'user',?)
                """
                        .strip();
        return Database.update(connection, sql, conversationId, content);
    }

    public int createModelMessage(Connection connection, long conversationId, String content)
            throws SQLException {
        String sql =
                """
                    INSERT ai_messages(conversation_id,role,content)
                    VALUES(?,'model',?)
                """
                        .strip();
        return Database.update(connection, sql, conversationId, content);
    }

    public int createChatHistory(Connection connection, long studentId, String title, String detail)
            throws SQLException {
        String sql =
                """
                    INSERT learning_history(student_id,title,type,category,status,detail)
                    VALUES(?,?,'AI_TUTOR',N'AI Tutor',N'Đã trao đổi',?)
                """
                        .strip();
        return Database.update(connection, sql, studentId, title, detail);
    }

    public List<Map<String, Object>> getConversations(Connection connection, long ownerId)
            throws SQLException {
        String sql =
                """
                    SELECT TOP (100) id,
                           created_at AS createdAt
                    FROM ai_conversations
                    WHERE owner_id=?
                    ORDER BY id DESC
                """
                        .strip();
        return Database.rows(connection, sql, ownerId);
    }

    public List<Map<String, Object>> getMessages(Connection connection, long conversationId)
            throws SQLException {
        String sql =
                """
                    SELECT TOP (200) id,
                           role,
                           content,
                           created_at AS createdAt
                    FROM ai_messages
                    WHERE conversation_id=?
                    ORDER BY id
                """
                        .strip();
        return Database.rows(connection, sql, conversationId);
    }
}
