package ratemycourse.entity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Catalog metadata. Missing text is empty, and a missing credit value stays absent. */
public final class Course {
    public final String code;
    public final String title;
    public final String faculty;
    public final Optional<BigDecimal> credits;
    public final List<String> calendarLinks;

    public Course(String code, String title, String faculty, BigDecimal credits, List<String> calendarLinks) {
        this.code = code;
        this.title = title;
        this.faculty = faculty;
        this.credits = Optional.ofNullable(credits);
        this.calendarLinks = List.copyOf(calendarLinks);
    }
}
