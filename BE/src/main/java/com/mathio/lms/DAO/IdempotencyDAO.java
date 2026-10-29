package com.mathio.lms.DAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Truy vấn SQL Server cho module Idempotency; dùng Connection của transaction đang xử lý. */
public class IdempotencyDAO {
    public Map<String, Object> getRequestForUpdate(
            Connection connection, long actorId, String requestKey) throws SQLException {
        String sql =
                """
                    SELECT route,
                           fingerprint,
                           response_json, status, lease_token,
                           CASE WHEN lease_until>SYSUTCDATETIME() THEN 1 ELSE 0 END AS live
                    FROM idempotency_keys WITH(UPDLOCK,HOLDLOCK)
                    WHERE actor_id=? AND request_key=?
                """
                        .strip();
        return Database.one(connection, sql, actorId, requestKey);
    }

    public int createAudit(Connection connection, long actorId, String action) throws SQLException {
        String sql =
                """
                    INSERT audit_logs(actor_id,action)
                    VALUES(?,?)
                """
                        .strip();
        return Database.update(connection, sql, actorId, action);
    }

    public int createRequest(
            Connection connection,
            long actorId,
            String requestKey,
            String route,
            String fingerprint,
            String response)
            throws SQLException {
        String sql =
                """
                    INSERT idempotency_keys(actor_id,request_key,route,fingerprint,response_json)
                    VALUES(?,?,?,?,?)
                """
                        .strip();
        return Database.update(connection, sql, actorId, requestKey, route, fingerprint, response);
    }
}
