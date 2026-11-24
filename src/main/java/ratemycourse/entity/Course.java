package ratemycourse.entity;

import java.util.Objects;

/** Immutable catalogue metadata. Searching cannot change stored courses. */
public final class Course {
    private final String code;
    private final String name;
    private final String department;
    private final int year;
    private final String description;
    private final String sourceUrl;

    public Course(String code, String name, String department, int year, String description) {
        this(code, name, department, year, description, "");
    }

    public Course(String code, String name, String department, int year, String description, String sourceUrl) {
        this.code = requireText(code);
        this.name = requireText(name);
        this.department = requireText(department);
        if (year < 1 || year > 4) {
            throw new IllegalArgumentException("Year must be between 1 and 4");
        }
        this.year = year;
        this.description = requireText(description);
        this.sourceUrl = Objects.requireNonNull(sourceUrl).strip();
    }

    private static String requireText(String value) {
        String text = Objects.requireNonNull(value).strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Course fields cannot be blank");
        }
        return text;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public int getYear() { return year; }
    public String getDescription() { return description; }
    public String getSourceUrl() { return sourceUrl; }
}
