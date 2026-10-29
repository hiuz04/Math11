package com.mathio.lms.model;

public class StoredResponse {
    private int status;
    private String body;

    @Override
    public boolean equals(Object other) {
        return other instanceof StoredResponse response
                && status == response.status
                && java.util.Objects.equals(body, response.body);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(status, body);
    }

    public StoredResponse() {}

    public StoredResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
