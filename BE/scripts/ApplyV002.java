import com.mathio.lms.DAO.DBContext;
import com.mathio.lms.model.Environment;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Explicit maintenance command; never called by application startup. */
public class ApplyV002 {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("math11_tutor_db"))
            throw new IllegalArgumentException("Usage: ApplyV002.java math11_tutor_db");
        Environment.load(Path.of(".env"));
        Path backups = Path.of("database", "backups").toAbsolutePath();
        Files.createDirectories(backups);
        Path backup = backups.resolve("math11_tutor_db_pre_V002_"
            + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            + "_" + java.util.UUID.randomUUID() + ".bak");
        try (DBContext db = DBContext.configured(); Connection c = db.getConnection()) {
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT DB_NAME()")) {
                if (!r.next() || !args[0].equals(r.getString(1)))
                    throw new IllegalStateException("Unexpected database; no changes made");
            }
            String disk = "N'" + backup.toString().replace("'", "''") + "'";
            execute(c, "BACKUP DATABASE [math11_tutor_db] TO DISK=" + disk
                + " WITH COPY_ONLY, CHECKSUM, NOINIT");
            execute(c, "RESTORE VERIFYONLY FROM DISK=" + disk + " WITH CHECKSUM");
            System.out.println("Verified copy-only backup: " + backup);
            String migration = Files.readString(Path.of("database/migrations/V002__backend_reliability.sql"));
            for (String batch : migration.split("(?im)^\\s*GO\\s*$"))
                if (!batch.isBlank()) execute(c, batch);
            System.out.println("V002 migration completed.");
        }
    }

    static void execute(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.setQueryTimeout(120);
            boolean result = s.execute(sql);
            while (true) {
                if (result) {
                    try (ResultSet r = s.getResultSet()) { while (r.next()) {} }
                } else if (s.getUpdateCount() == -1) break;
                result = s.getMoreResults();
            }
            for (SQLWarning w = s.getWarnings(); w != null; w = w.getNextWarning())
                System.out.println(w.getMessage());
        }
    }
}
