package ratemycourse.usecase.search;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ratemycourse.entity.Course;

/** UC1 output data derived from the shared entity without changing its UC3 contract. */
public final class CourseData {
    private static final Pattern CODE = Pattern.compile("([A-Z]{2,5})([0-9])[0-9]{2}[A-Z][0-9]?");
    private final Course course;
    private final String department;
    private final int year;

    public CourseData(Course course) {
        this.course = course;
        Matcher match = CODE.matcher(course.code.toUpperCase(Locale.ROOT));
        boolean recognized = match.matches();
        department = recognized ? match.group(1) : "";
        year = recognized ? Integer.parseInt(match.group(2)) : 0;
    }

    public String getCode() { return course.code; }
    public String getName() { return course.title; }
    public String getFaculty() { return course.faculty; }
    public String getDepartment() { return department; }
    public int getYear() { return year; }
    public String getCredits() { return course.credits.map(value -> value.toPlainString()).orElse("Not provided"); }
    public List<String> getCalendarLinks() { return course.calendarLinks; }
    public String getSourceUrl() { return course.calendarLinks.isEmpty() ? "" : course.calendarLinks.get(0); }
    public String getDescription() { return "Description not provided by the public catalog. See official listings."; }
}
