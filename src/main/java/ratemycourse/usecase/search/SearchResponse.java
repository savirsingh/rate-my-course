package ratemycourse.usecase.search;

import java.util.Collections;
import java.util.List;

/** A successful empty list and a loading failure are separate outcomes. */
public final class SearchResponse {
    private final List<CourseData> courses;
    private final List<String> departments;
    private final String error;

    private SearchResponse(List<CourseData> courses, List<String> departments, String error) {
        this.courses = List.copyOf(courses);
        this.departments = List.copyOf(departments);
        this.error = error;
    }

    public static SearchResponse success(List<CourseData> courses, List<String> departments) {
        return new SearchResponse(courses, departments, "");
    }

    public static SearchResponse failure() {
        return new SearchResponse(Collections.emptyList(), Collections.emptyList(),
                "Could not load the course catalogue. Please retry.");
    }

    public boolean isSuccess() { return error.isEmpty(); }
    public List<CourseData> getCourses() { return courses; }
    public List<String> getDepartments() { return departments; }
    public String getError() { return error; }
}
