package com.mathio.lms.model;

import com.mathio.lms.DAO.Database;
import com.mathio.lms.DAO.IdempotencyDAO;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class IdempotencyService {
    private record Pending(long actorId, String key, String token, String route) {}

    private static final ThreadLocal<Pending> PENDING = new ThreadLocal<>();

    /** Save the response atomically with the business result in phase C. */
    public static void completeInTransaction(java.sql.Connection c, Object body)
            throws java.sql.SQLException {
        Pending pending = PENDING.get();
        if (pending == null) return;
        int updated =
                Database.update(
                        c,
                        "UPDATE idempotency_keys SET status='COMPLETED',response_json=? WHERE"
                                + " actor_id=? AND request_key=? AND lease_token=? AND"
                                + " status='PROCESSING'",
                        Json.stringify(new StoredResponse(200, Json.stringify(body))),
                        pending.actorId(),
                        pending.key(),
                        pending.token());
        ApiException.require(updated == 1, 409, "Phiên xử lý đã được thay thế.");
        Database.update(
                c,
                "INSERT audit_logs(actor_id,action) VALUES(?,?)",
                pending.actorId(),
                pending.route().substring(0, Math.min(100, pending.route().length())));
    }

    @FunctionalInterface
    public interface Action {
        StoredResponse run() throws Exception;
    }

    private final Database database;
    private final IdempotencyDAO idempotencyDAO = new IdempotencyDAO();

    public IdempotencyService(Database database) {
        this.database = database;
    }

    public StoredResponse execute(
            Actor currentUser, String key, String route, String body, Action action) {
        if (java.util.Set.of(
                        "POST /ai/essays/evaluate",
                        "POST /ai/essays/evaluate-retry",
                        "POST /ai/tutor/start",
                        "POST /ai/questions/generate",
                        "POST /ai/exams/generate",
                        "POST /ai/chat",
                        "POST /admin/config/gemini/test")
                .contains(route)) return executeDetached(currentUser, key, route, body, action);
        ApiException.require(
                key.matches("[A-Za-z0-9_-]{16,100}"), 400, "Idempotency key không hợp lệ.");
        return database.transaction(
                connection -> {
                    String fingerprint =
                            HexFormat.of()
                                    .formatHex(
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(
                                                            (route + "\n" + body)
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)));
                    var old =
                            idempotencyDAO.getRequestForUpdate(
                                    connection, currentUser.getId(), key);
                    if (old != null) {
                        ApiException.require(
                                !"PROCESSING".equals(old.get("status")),
                                409,
                                "Yêu cầu đang xử lý.");
                        ApiException.require(
                                old.get("route").equals(route)
                                        && old.get("fingerprint").equals(fingerprint),
                                409,
                                "Idempotency key đã được dùng với nội dung khác.");
                        var saved = Json.parse(old.get("response_json").toString());
                        return new StoredResponse(
                                saved.path("status").asInt(), saved.path("body").asText());
                    }
                    StoredResponse result = action.run();
                    idempotencyDAO.createAudit(
                            connection,
                            currentUser.getId(),
                            route.substring(0, Math.min(100, route.length())));
                    idempotencyDAO.createRequest(
                            connection,
                            currentUser.getId(),
                            key,
                            route,
                            fingerprint,
                            Json.stringify(result));
                    return result;
                });
    }

    /** Persist the request lease, release JDBC, run the long action, then persist its response. */
    private StoredResponse executeDetached(
            Actor actor, String key, String route, String body, Action action) {
        database.requireNoTransaction();
        ApiException.require(
                key.matches("[A-Za-z0-9_-]{16,100}"), 400, "Idempotency key không hợp lệ.");
        String fingerprint = Secrets.sha256((route + "\n" + body).getBytes(StandardCharsets.UTF_8));
        String token = java.util.UUID.randomUUID().toString();
        StoredResponse replay =
                database.transaction(
                        c -> {
                            var old = idempotencyDAO.getRequestForUpdate(c, actor.getId(), key);
                            if (old != null) {
                                ApiException.require(
                                        route.equals(old.get("route"))
                                                && fingerprint.equals(old.get("fingerprint")),
                                        409,
                                        "Idempotency key đã dùng với nội dung khác.");
                                if ("COMPLETED".equals(old.get("status"))) {
                                    var saved = Json.parse(old.get("response_json").toString());
                                    return new StoredResponse(
                                            saved.path("status").asInt(),
                                            saved.path("body").asText());
                                }
                                ApiException.require(
                                        !"PROCESSING".equals(old.get("status"))
                                                || ((Number) old.get("live")).intValue() == 0,
                                        409,
                                        "Yêu cầu đang xử lý. Hãy thử lại sau.");
                                Database.update(
                                        c,
                                        "UPDATE idempotency_keys SET"
                                            + " status='PROCESSING',lease_token=?,lease_until=DATEADD(SECOND,900,SYSUTCDATETIME())"
                                            + " WHERE actor_id=? AND request_key=?",
                                        token,
                                        actor.getId(),
                                        key);
                            } else
                                Database.update(
                                        c,
                                        "INSERT"
                                            + " idempotency_keys(actor_id,request_key,route,fingerprint,response_json,status,lease_token,lease_until)"
                                            + " VALUES(?,?,?,?,N'{}','PROCESSING',?,DATEADD(SECOND,900,SYSUTCDATETIME()))",
                                        actor.getId(),
                                        key,
                                        route,
                                        fingerprint,
                                        token);
                            return null;
                        });
        if (replay != null) return replay;
        PENDING.set(new Pending(actor.getId(), key, token, route));
        try {
            StoredResponse result = action.run();
            return database.transaction(
                    c -> {
                        var completed = idempotencyDAO.getRequestForUpdate(c, actor.getId(), key);
                        if (completed != null
                                && token.equals(completed.get("lease_token"))
                                && "COMPLETED".equals(completed.get("status"))) {
                            var saved = Json.parse(completed.get("response_json").toString());
                            return new StoredResponse(
                                    saved.path("status").asInt(), saved.path("body").asText());
                        }
                        int updated =
                                Database.update(
                                        c,
                                        "UPDATE idempotency_keys SET"
                                            + " status='COMPLETED',response_json=? WHERE actor_id=?"
                                            + " AND request_key=? AND lease_token=? AND"
                                            + " status='PROCESSING'",
                                        Json.stringify(result),
                                        actor.getId(),
                                        key,
                                        token);
                        ApiException.require(updated == 1, 409, "Phiên xử lý đã được thay thế.");
                        idempotencyDAO.createAudit(
                                c,
                                actor.getId(),
                                route.substring(0, Math.min(100, route.length())));
                        return result;
                    });
        } catch (Exception e) {
            try {
                database.transaction(
                        c -> {
                            Database.update(
                                    c,
                                    "UPDATE idempotency_keys SET status='FAILED' WHERE actor_id=?"
                                            + " AND request_key=? AND lease_token=? AND"
                                            + " status='PROCESSING'",
                                    actor.getId(),
                                    key,
                                    token);
                            return null;
                        });
            } catch (RuntimeException cleanup) {
                e.addSuppressed(cleanup);
            }
            if (e instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Unable to execute idempotent action", e);
        } finally {
            PENDING.remove();
        }
    }
}
