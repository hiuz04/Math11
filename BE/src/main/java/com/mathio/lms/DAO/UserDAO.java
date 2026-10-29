package com.mathio.lms.DAO;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.model.Actor;
import com.mathio.lms.model.ApiException;
import com.mathio.lms.model.Json;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Locale;
import java.util.Set;

public class UserDAO {
    private final Database database;

    public UserDAO(Database database) {
        this.database = database;
    }

    public Object getUsers(Actor currentUser) {
        return getUsers(currentUser, 0, 100);
    }

    public Object getUsers(Actor currentUser, int offset, int limit) {
        currentUser.requireStaff();
        return database.read(
                connection ->
                        Database.rows(
                                connection,
                                """
    SELECT DISTINCT u.id,
           u.name,
           u.email,
           u.role,
           u.active,
           u.created_at AS createdAt
    FROM users u
    WHERE u.deleted=0 AND (?=1 OR u.role='STUDENT' AND EXISTS(SELECT 1
    FROM course_enrollments e
    JOIN courses c ON c.id=e.course_id
    WHERE e.student_id=u.id AND c.teacher_id=? AND c.deleted=0))
    ORDER BY u.id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
""",
                                currentUser.isAdmin(),
                                currentUser.getId(),
                                offset,
                                limit));
    }

    public Object saveUser(Actor currentUser, long id, JsonNode input) {
        currentUser.requireAdmin();
        String name = Json.text(input, "name", 150),
                email = Json.text(input, "email", 254).toLowerCase(Locale.ROOT),
                role = Json.text(input, "role", 20);
        ApiException.require(
                email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                        && Set.of("ADMIN", "TEACHER", "STUDENT").contains(role),
                400,
                "Email hoặc vai trò không hợp lệ.");
        return database.transaction(
                connection -> {
                    ApiException.require(
                            Database.one(
                                            connection,
                                            """
                                                SELECT id
                                                FROM users
                                                WHERE email=? AND id<>?
                                            """,
                                            email,
                                            id)
                                    == null,
                            409,
                            "Email đã được sử dụng.");
                    if (id == 0) {
                        String password = Json.password(input, "password", 1);
                        ApiException.require(
                                password.length() >= 10, 400, "Mật khẩu phải có ít nhất 10 ký tự.");
                        long next =
                                Database.insert(
                                        connection,
                                        """
                                            INSERT users(name,email,role,password_hash)
                                            VALUES(?,?,?,?)
                                        """,
                                        name,
                                        email,
                                        role,
                                        BCrypt.hashpw(password, BCrypt.gensalt(12)));
                        Database.update(
                                connection,
                                """
                                    INSERT user_profiles(user_id)
                                    VALUES(?)
                                """,
                                next);
                        Database.update(
                                connection,
                                "UPDATE users SET active=? WHERE id=?",
                                input.path("active").asBoolean(true),
                                next);
                        AccessDAO.audit(connection, currentUser, "USER_CREATED", next);
                        return Database.one(
                                connection,
                                """
                                    SELECT id,
                                           name,
                                           email,
                                           role,
                                           active
                                    FROM users
                                    WHERE id=?
                                """,
                                next);
                    }
                    ApiException.require(
                            id != currentUser.getId()
                                    || role.equals("ADMIN") && input.path("active").asBoolean(true),
                            409,
                            "Không thể tự khóa hoặc hạ quyền tài khoản đang dùng.");
                    ApiException.require(
                            Database.update(
                                            connection,
                                            """
    UPDATE users
    SET name=?,email=?,role=?,active=?,session_version=session_version+1
    WHERE id=? AND deleted=0
""",
                                            name,
                                            email,
                                            role,
                                            input.path("active").asBoolean(true),
                                            id)
                                    == 1,
                            404,
                            "Không tìm thấy tài khoản.");
                    Database.update(
                            connection,
                            """
                                INSERT audit_logs(actor_id,action,resource_id)
                                VALUES(?,'USER_UPDATED',?)
                            """,
                            currentUser.getId(),
                            id);
                    return Database.one(
                            connection,
                            """
                                SELECT id,
                                       name,
                                       email,
                                       role,
                                       active
                                FROM users
                                WHERE id=?
                            """,
                            id);
                });
    }

    public void deleteUser(Actor currentUser, long id) {
        currentUser.requireAdmin();
        ApiException.require(id != currentUser.getId(), 409, "Không thể tự xóa tài khoản.");
        database.transaction(
                connection -> {
                    ApiException.require(
                            Database.update(
                                            connection,
                                            """
    UPDATE users
    SET deleted=1,active=0,session_version=session_version+1
    WHERE id=? AND deleted=0
""",
                                            id)
                                    == 1,
                            404,
                            "Không tìm thấy tài khoản.");
                    Database.update(
                            connection,
                            """
                                INSERT audit_logs(actor_id,action,resource_id)
                                VALUES(?,'USER_DELETED',?)
                            """,
                            currentUser.getId(),
                            id);
                    return null;
                });
    }
}
