package com.mathio.lms.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Objects;

/** Thông tin tài khoản đã xác thực, lấy từ database và lưu trong session. */
public class Actor implements Serializable {
    private static final long serialVersionUID = 1L;
    private long id;
    private String role;
    private int version;

    public Actor() {}

    public Actor(long id, String role, int version) {
        this.id = id;
        this.role = role;
        this.version = version;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    @JsonIgnore
    public boolean isStudent() {
        return "STUDENT".equals(role);
    }

    @JsonIgnore
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public void requireStaff() {
        ApiException.require(
                isAdmin() || "TEACHER".equals(role),
                403,
                "Chỉ giáo viên hoặc quản trị viên được phép.");
    }

    public void requireAdmin() {
        ApiException.require(isAdmin(), 403, "Chỉ quản trị viên được phép.");
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Actor actor)) {
            return false;
        }
        return id == actor.id && version == actor.version && Objects.equals(role, actor.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, role, version);
    }
}
