package com.mathio.lms.DAO;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Json;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

/** The submission row must be locked before accessing its single AI lease. */
public final class AiJobDAO {
    public record Job(
            long submissionId,
            String kind,
            String token,
            long revision,
            String fingerprint,
            JsonNode content) {}

    public Map<String, Object> get(Connection c, long id) throws SQLException {
        return Database.one(
                c,
                "SELECT *, CASE WHEN lease_until>SYSUTCDATETIME() THEN 1 ELSE 0 END AS live FROM"
                        + " essay_ai_jobs WHERE submission_id=?",
                id);
    }

    public Job reserve(
            Connection c, long id, String kind, long revision, String fingerprint, JsonNode content)
            throws SQLException {
        var old = get(c, id);
        ApiException.require(
                old == null
                        || !"PROCESSING".equals(old.get("status"))
                        || ((Number) old.get("live")).intValue() == 0,
                409,
                "AI đang xử lý bài này. Hãy thử lại sau.");
        String token = UUID.randomUUID().toString();
        if (old != null && "PROCESSING".equals(old.get("status")))
            Database.update(
                    c,
                    "UPDATE essay_ai_job_attempts SET"
                            + " status='EXPIRED',completed_at=SYSUTCDATETIME() WHERE token=? AND"
                            + " status='PROCESSING'",
                    old.get("token"));
        Database.update(
                c,
                "INSERT essay_ai_job_attempts(submission_id,kind,token,revision,status)"
                        + " VALUES(?,?,?,?,'PROCESSING')",
                id,
                kind,
                token,
                revision);
        if (old == null)
            Database.update(
                    c,
                    "INSERT"
                        + " essay_ai_jobs(submission_id,kind,token,status,revision,fingerprint,content_json,lease_until)"
                        + " VALUES(?,?,?,'PROCESSING',?,?,?,DATEADD(SECOND,300,SYSUTCDATETIME()))",
                    id,
                    kind,
                    token,
                    revision,
                    fingerprint,
                    Json.stringify(content));
        else
            Database.update(
                    c,
                    "UPDATE essay_ai_jobs SET"
                        + " kind=?,token=?,status='PROCESSING',revision=?,fingerprint=?,content_json=?,lease_until=DATEADD(SECOND,300,SYSUTCDATETIME()),updated_at=SYSUTCDATETIME()"
                        + " WHERE submission_id=?",
                    kind,
                    token,
                    revision,
                    fingerprint,
                    Json.stringify(content),
                    id);
        return new Job(id, kind, token, revision, fingerprint, content);
    }

    public void validate(Connection c, Job job, long currentRevision) throws SQLException {
        var row = get(c, job.submissionId());
        ApiException.require(
                row != null
                        && job.token().equals(row.get("token"))
                        && "PROCESSING".equals(row.get("status"))
                        && currentRevision == job.revision(),
                409,
                "Kết quả AI đã cũ. Hãy tải lại bài làm.");
    }

    public void finish(Connection c, Job job, boolean success) throws SQLException {
        Database.update(
                c,
                "UPDATE essay_ai_job_attempts SET status=?,completed_at=SYSUTCDATETIME() WHERE"
                        + " token=? AND status='PROCESSING'",
                success ? "COMPLETED" : "FAILED",
                job.token());
        Database.update(
                c,
                "UPDATE essay_ai_jobs SET status=?,updated_at=SYSUTCDATETIME() WHERE"
                        + " submission_id=? AND token=? AND status='PROCESSING'",
                success ? "COMPLETED" : "FAILED",
                job.submissionId(),
                job.token());
    }
}
