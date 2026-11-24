package ratemycourse.usecase.gateway;

import java.util.List;
import ratemycourse.entity.Course;

/** One immutable API result page; total is the match count before pagination. */
public final class CourseSearchPage {
    public final List<Course> courses;
    public final int total;
    public final int page;
    public final int pageSize;

    public CourseSearchPage(List<Course> courses, int total, int page, int pageSize) {
        this.courses = List.copyOf(courses);
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }
}
