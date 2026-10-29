package com.mathio.lms.model;

/** Chi tiết bài giảng kèm tiến độ của học sinh hiện tại. */
public class LessonView extends com.mathio.lms.model.Lesson {
    private Integer completed;
    private Integer elapsedSeconds;

    public LessonView() {}

    public Integer getCompleted() {
        return completed;
    }

    public void setCompleted(Integer completed) {
        this.completed = completed;
    }

    public Integer getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void setElapsedSeconds(Integer elapsedSeconds) {
        this.elapsedSeconds = elapsedSeconds;
    }
}
