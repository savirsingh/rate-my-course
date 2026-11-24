package ratemycourse.usecase.search;

import ratemycourse.entity.Course;

/** Output data for search results and the metadata preview, independent of Swing. */
public final class CourseData {
    private final String code;
    private final String name;
    private final String department;
    private final int year;
    private final String description;
    private final String sourceUrl;

    public CourseData(Course course) {
        code = course.getCode();
        name = course.getName();
        department = course.getDepartment();
        year = course.getYear();
        description = course.getDescription();
        sourceUrl = course.getSourceUrl();
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public int getYear() { return year; }
    public String getDescription() { return description; }
    public String getSourceUrl() { return sourceUrl; }
}
