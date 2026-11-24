package ratemycourse.dataaccess;

import java.util.List;
import java.util.Map;
import ratemycourse.entity.Review;
import ratemycourse.usecase.gateway.ReviewRepository;

/** Fictional reviews for the UC3 demo, keyed by the catalog's normalized course codes. */
public final class DemoReviewRepository implements ReviewRepository {
    private final Map<String, List<Review>> reviews = Map.of(
            "CSC207H1", List.of(new Review(5, 3, 4), new Review(4, 4, 5), new Review(4, 2, 3)),
            "MAT237Y1", List.of(new Review(5, 5, 5), new Review(3, 4, 5)));

    @Override
    public List<Review> findByCourseCode(String code) {
        return reviews.getOrDefault(code, List.of());
    }
}
