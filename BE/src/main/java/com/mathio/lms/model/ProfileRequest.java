package com.mathio.lms.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;

/** Dữ liệu hồ sơ được validate trước khi cập nhật. */
public class ProfileRequest {
    private String name;
    private String phone;
    private String birthDate;

    public ProfileRequest() {}

    public ProfileRequest(String name, String phone, String birthDate) {
        this.name = name;
        this.phone = phone;
        this.birthDate = birthDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public static ProfileRequest from(JsonNode input) {
        String name = Json.text(input, "name", 150),
                phone = input.path("phone").asText(""),
                birth = input.path("birthDate").asText("");
        ApiException.require(phone.matches("[+0-9 ()-]{0,30}"), 400, "Số điện thoại không hợp lệ.");
        if (!birth.isBlank())
            try {
                ApiException.require(
                        !LocalDate.parse(birth).isAfter(LocalDate.now()),
                        400,
                        "Ngày sinh không hợp lệ.");
            } catch (java.time.format.DateTimeParseException e) {
                throw new ApiException(400, "Ngày sinh phải theo định dạng YYYY-MM-DD.");
            }
        return new ProfileRequest(name, phone, birth);
    }
}
