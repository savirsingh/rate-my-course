package ratemycourse.usecase.search;

import java.util.List;
import ratemycourse.entity.Course;

/** Read-only port owned by the use case, implemented by external data adapters. */
public interface CourseCatalogue {
    List<Course> loadCourses() throws CatalogueException;
}
