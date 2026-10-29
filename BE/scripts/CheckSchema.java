import com.mathio.lms.DAO.*;
import com.mathio.lms.model.*;
import java.nio.file.Path;

/** Read-only check using exactly the backend's JDBC and .env configuration. */
public class CheckSchema {
    public static void main(String[] args) {
        Environment.load(Path.of(args.length == 0 ? ".env" : args[0]));
        try (Database db = Database.configured()) {
            boolean upgraded = db.read(c -> {
                System.out.println(Json.stringify(Database.rows(c,
                    "SELECT DB_NAME() AS databaseName, @@SERVERNAME AS serverName")));
                System.out.println("V002 status: " + Json.stringify(Database.rows(c,
                    "SELECT COL_LENGTH('dbo.questions','course_id') AS questionCourseId, "
                    + "COL_LENGTH('dbo.questions','published') AS questionPublished, "
                    + "COL_LENGTH('dbo.exams','course_id') AS examCourseId, "
                    + "COL_LENGTH('dbo.exams','results_published') AS resultsPublished, "
                    + "OBJECT_ID('dbo.schema_versions','U') AS versionTable")));
                if (Database.one(c, "SELECT OBJECT_ID('dbo.schema_versions','U') AS id").get("id") != null)
                    System.out.println("Versions: " + Json.stringify(Database.rows(c,
                        "SELECT version,applied_at FROM dbo.schema_versions ORDER BY version")));
                System.out.println("V002 base tables: " + Json.stringify(Database.rows(c,
                    "SELECT name FROM sys.tables WHERE name IN ('questions','exams','essay_submissions',"
                    + "'idempotency_keys','tutor_step_attempts','exam_attempts','courses','users','ai_messages','audit_logs') ORDER BY name")));
                return Database.one(c, "SELECT OBJECT_ID('dbo.schema_versions','U') AS id").get("id") != null
                    && Database.one(c, "SELECT version FROM dbo.schema_versions WHERE version=2") != null;
            });
            if (!upgraded) {
                System.err.println("V002 is missing. Apply database/migrations/V002__backend_reliability.sql after a verified backup.");
                System.exit(1);
            }
            var actors = db.read(c -> Database.rows(c,
                "SELECT MIN(id) AS id, role FROM users WHERE active=1 AND deleted=0 GROUP BY role"));
            for (var row : actors) {
                Actor actor = new Actor(((Number)row.get("id")).longValue(), row.get("role").toString(), 1);
                CatalogDAO catalog = new CatalogDAO(db);
                System.out.println(actor.getRole() + " chapters="
                    + Json.MAPPER.valueToTree(catalog.getChapters(actor, 0)).size()
                    + " courses=" + Json.MAPPER.valueToTree(catalog.getCourses(actor)).size()
                    + " lessons=" + Json.MAPPER.valueToTree(catalog.getLessons(actor, 0)).size()
                    + " exercises=" + Json.MAPPER.valueToTree(new QuestionDAO(db).getExercises(actor)).size()
                    + " exams=" + Json.MAPPER.valueToTree(new ExamService(db, new QuestionDAO(db)).getExams(actor)).size());
            }
        }
    }
}
