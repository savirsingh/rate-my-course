package ratemycourse.usecase.gateway;

import java.util.Optional;
import ratemycourse.entity.Course;

public interface CourseCatalog {
    /** Looks up one normalized code. Empty means the catalog has no matching course. */
    Optional<Course> findByCode(String code) throws DataAccessException;
}
