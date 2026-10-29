package com.mathio.lms.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.mathio.lms.DAO.AuthDAO;
import com.mathio.lms.DAO.Database;

import org.mindrot.jbcrypt.BCrypt;

import java.util.Locale;
import java.util.Map;

public class AuthService {
    private final Database database;
    private final AuthDAO authDAO = new AuthDAO();
    private static final String DUMMY_PASSWORD_HASH =
            BCrypt.hashpw("invalid-password", BCrypt.gensalt(12));

    public AuthService(Database database) {
        this.database = database;
    }

    public Actor login(JsonNode input) {
        String email = Json.text(input, "email", 254).toLowerCase(Locale.ROOT),
                password = Json.password(input, "password", 1);
        var user = database.read(connection -> authDAO.getLoginAccount(connection, email));
        boolean valid =
                BCrypt.checkpw(
                        password, user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash());
        ApiException.require(
                user != null && valid && user.getActive(),
                401,
                "Email hoặc mật khẩu không chính xác.");
        return new Actor(user.getId(), user.getRole(), user.getSessionVersion());
    }

    public Actor validateSession(Actor actor) {
        ApiException.require(actor != null, 401, "Vui lòng đăng nhập.");
        return database.read(
                connection -> {
                    var user = authDAO.getActiveSession(connection, actor.getId());
                    ApiException.require(
                            user != null
                                    && user.getRole().equals(actor.getRole())
                                    && user.getSessionVersion() == actor.getVersion(),
                            401,
                            "Phiên đăng nhập đã hết hạn.");
                    return actor;
                });
    }

    public Map<String, Object> getProfile(Actor currentUser) {
        return database.read(connection -> authDAO.getProfile(connection, currentUser.getId()));
    }

    public void updateProfile(Actor currentUser, JsonNode input) {
        var profile = com.mathio.lms.model.ProfileRequest.from(input);
        database.transaction(
                connection -> {
                    authDAO.updateProfileName(connection, profile.getName(), currentUser.getId());
                    authDAO.updateProfileDetails(
                            connection,
                            profile.getPhone(),
                            profile.getBirthDate(),
                            currentUser.getId());
                    com.mathio.lms.DAO.AccessDAO.audit(
                            connection, currentUser, "PROFILE_UPDATED", currentUser.getId());
                    return null;
                });
    }

    public void changePassword(Actor currentUser, JsonNode input) {
        String oldPassword = Json.password(input, "oldPassword", 1),
                newPassword = Json.password(input, "newPassword", 10);
        ApiException.require(
                newPassword.length() >= 10, 400, "Mật khẩu mới phải có ít nhất 10 ký tự.");
        database.transaction(
                connection -> {
                    var user = authDAO.getPasswordForUpdate(connection, currentUser.getId());
                    ApiException.require(
                            BCrypt.checkpw(oldPassword, user.getPasswordHash()),
                            400,
                            "Mật khẩu hiện tại không chính xác.");
                    authDAO.updatePassword(
                            connection,
                            BCrypt.hashpw(newPassword, BCrypt.gensalt(12)),
                            currentUser.getId());
                    com.mathio.lms.DAO.AccessDAO.audit(
                            connection, currentUser, "PASSWORD_CHANGED", currentUser.getId());
                    return null;
                });
    }
}
