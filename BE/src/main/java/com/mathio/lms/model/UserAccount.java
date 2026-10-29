package com.mathio.lms.model;

public class UserAccount {
    private long id;
    private String role;
    private String passwordHash;
    private boolean active;
    private int sessionVersion;

    public UserAccount() {}

    public UserAccount(
            long id, String role, String passwordHash, boolean active, int sessionVersion) {
        this.id = id;
        this.role = role;
        this.passwordHash = passwordHash;
        this.active = active;
        this.sessionVersion = sessionVersion;
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

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    public void setSessionVersion(int sessionVersion) {
        this.sessionVersion = sessionVersion;
    }
}
