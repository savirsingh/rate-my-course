package ratemycourse.usecase.gateway;

import java.util.List;
import ratemycourse.entity.Review;

public interface ReviewRepository {
    /** Returns reviews for a normalized course code. Empty means a successful lookup with no reviews. */
    List<Review> findByCourseCode(String code) throws DataAccessException;
}
