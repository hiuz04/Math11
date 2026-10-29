package com.mathio.lms.DAO;

import com.mathio.lms.model.UserAccount;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/** Truy vấn SQL Server cho module Auth; dùng Connection của transaction đang xử lý. */
public class AuthDAO {
    public UserAccount getLoginAccount(Connection connection, String email) throws SQLException {
        String sql =
                """
                    SELECT id,
                           role,
                           password_hash,
                           active,
                           session_version
                    FROM users
                    WHERE email=? AND deleted=0
                """
                        .strip();
        return mapUserAccount(Database.one(connection, sql, email));
    }

    public UserAccount getActiveSession(Connection connection, long userId) throws SQLException {
        String sql =
                """
                    SELECT role,
                           session_version
                    FROM users
                    WHERE id=? AND active=1 AND deleted=0
                """
                        .strip();
        return mapUserAccount(Database.one(connection, sql, userId));
    }

    public Map<String, Object> getProfile(Connection connection, long userId) throws SQLException {
        String sql =
                """
                    SELECT u.id,
                           u.email,
                           u.name,
                           u.role,
                           u.created_at AS createdAt,
                           p.birth_date AS birthDate,
                           p.phone
                    FROM users u
                    LEFT JOIN user_profiles p ON p.user_id=u.id
                    WHERE u.id=?
                """
                        .strip();
        return Database.one(connection, sql, userId);
    }

    public int updateProfileName(Connection connection, String name, long userId)
            throws SQLException {
        String sql =
                """
                    UPDATE users
                    SET name=?
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, name, userId);
    }

    public int updateProfileDetails(
            Connection connection, String phone, String birthDate, long userId)
            throws SQLException {
        String sql =
                """
                    UPDATE user_profiles
                    SET phone=?,birth_date=TRY_CONVERT(date,?)
                    WHERE user_id=?
                """
                        .strip();
        return Database.update(connection, sql, phone, birthDate, userId);
    }

    public UserAccount getPasswordForUpdate(Connection connection, long userId)
            throws SQLException {
        String sql =
                """
                    SELECT password_hash
                    FROM users WITH (UPDLOCK)
                    WHERE id=?
                """
                        .strip();
        return mapUserAccount(Database.one(connection, sql, userId));
    }

    public int updatePassword(Connection connection, String passwordHash, long userId)
            throws SQLException {
        String sql =
                """
                    UPDATE users
                    SET password_hash=?,session_version=session_version+1
                    WHERE id=?
                """
                        .strip();
        return Database.update(connection, sql, passwordHash, userId);
    }

    private UserAccount mapUserAccount(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        UserAccount user = new UserAccount();
        if (row.get("id") instanceof Number id) {
            user.setId(id.longValue());
        }
        user.setRole((String) row.get("role"));
        user.setPasswordHash((String) row.get("password_hash"));
        user.setActive(Boolean.TRUE.equals(row.get("active")));
        if (row.get("session_version") instanceof Number version) {
            user.setSessionVersion(version.intValue());
        }
        return user;
    }
}
