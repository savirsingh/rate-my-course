package ratemycourse.usecase.compare;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import ratemycourse.entity.Course;

public final class ComparisonOutput {
    public final Column first;
    public final Column second;

    public ComparisonOutput(Column first, Column second) {
        this.first = first;
        this.second = second;
    }

    public static final class Column {
        public final String code;
        public final String title;
        public final String faculty;
        public final Optional<BigDecimal> credits;
        public final List<String> calendarLinks;
        /** Absent on a review read failure. A successful empty lookup has a summary with count zero. */
        public final Optional<ReviewSummary> reviews;

        public Column(Course course, Optional<ReviewSummary> reviews) {
            this.code = course.code;
            this.title = course.title;
            this.faculty = course.faculty;
            this.credits = course.credits;
            this.calendarLinks = course.calendarLinks;
            this.reviews = reviews;
        }
    }
}
