package ratemycourse.interfaceadapter.compare;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ratemycourse.entity.Course;
import ratemycourse.entity.Review;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel.Column;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel.State;
import ratemycourse.usecase.compare.ComparisonException;
import ratemycourse.usecase.compare.ComparisonException.Reason;
import ratemycourse.usecase.compare.ComparisonOutput;
import ratemycourse.usecase.compare.ReviewSummary;
import ratemycourse.usecase.gateway.DataAccessException;

import static org.junit.jupiter.api.Assertions.*;

class CompareCoursesPresenterTest {
    private final CompareCoursesViewModel model = new CompareCoursesViewModel("DEMO reviews");
    private final CompareCoursesPresenter presenter = new CompareCoursesPresenter(model);
    private final Course first = new Course("CSC207H1", "Example", "Faculty", new BigDecimal("0.5"),
            List.of("https://example.edu/calendar"));
    private final Course second = new Course("MAT237Y1", "", "", null, List.of());

    private ComparisonOutput comparison(Optional<ReviewSummary> firstReviews, Optional<ReviewSummary> secondReviews) {
        return new ComparisonOutput(new ComparisonOutput.Column(first, firstReviews),
                new ComparisonOutput.Column(second, secondReviews));
    }

    @Test
    void formatsRatingsAndMetadataWithoutLosingSelectionOrder() {
        ReviewSummary rated = ReviewSummary.from(List.of(new Review(4, 2, 1), new Review(4, 4, 2), new Review(5, 3, 2)));
        presenter.presentComparison(comparison(Optional.of(rated), Optional.of(ReviewSummary.from(List.of()))));

        List<Column> courses = model.getState().courses;
        assertEquals(2, courses.size());
        Column left = courses.get(0);
        Column right = courses.get(1);
        assertEquals(List.of("CSC207H1", "Example", "Faculty", "0.5"),
                List.of(left.code, left.title, left.faculty, left.credits));
        assertEquals(first.calendarLinks, left.calendarLinks);
        assertEquals(List.of("3", "4.33", "3.00", "1.67", ""),
                List.of(left.reviews.count, left.reviews.enjoyment, left.reviews.difficulty, left.reviews.workload, left.reviews.note));
        assertEquals(List.of("MAT237Y1", "Not provided", "Not provided", "Not provided"),
                List.of(right.code, right.title, right.faculty, right.credits));
        assertTrue(right.calendarLinks.isEmpty());
        assertEquals("DEMO reviews", model.reviewSource);
    }

    @Test
    void distinguishesAReviewReadFailureFromAKnownEmptyList() {
        presenter.presentComparison(comparison(Optional.empty(), Optional.of(ReviewSummary.from(List.of()))));
        Column left = model.getState().courses.get(0);
        Column right = model.getState().courses.get(1);

        assertEquals(List.of("Unavailable", "Unavailable", "Unavailable", "Unavailable"),
                List.of(left.reviews.count, left.reviews.enjoyment, left.reviews.difficulty, left.reviews.workload));
        assertEquals("Could not load reviews for CSC207H1.", left.reviews.note);
        assertEquals(List.of("0", "N/A", "N/A", "N/A"),
                List.of(right.reviews.count, right.reviews.enjoyment, right.reviews.difficulty, right.reviews.workload));
        assertEquals("MAT237Y1 has no reviews.", right.reviews.note);
        assertTrue(model.getState().errorMessage.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"INVALID_FIRST_CODE, first course", "INVALID_SECOND_CODE, second course",
            "IDENTICAL_CODES, two different courses", "COURSE_NOT_FOUND, MAT237Y1 was not found",
            "CATALOG_FAILURE, Could not look up MAT237Y1. Catalog offline."})
    void publishesFailureAndClearsPreviousResultsThenRecovers(Reason reason, String expected) {
        List<State> notifications = new ArrayList<>();
        model.addListener(event -> notifications.add(model.getState()));
        ComparisonOutput result = comparison(Optional.empty(), Optional.empty());
        presenter.presentComparison(result);
        DataAccessException cause = reason == Reason.CATALOG_FAILURE ? new DataAccessException("Catalog offline.") : null;
        presenter.presentFailure(new ComparisonException(reason, second.code, cause));
        presenter.presentComparison(result);

        assertEquals(3, notifications.size());
        assertEquals(2, notifications.get(0).courses.size());
        assertTrue(notifications.get(1).courses.isEmpty());
        assertTrue(notifications.get(1).errorMessage.contains(expected));
        assertEquals(2, notifications.get(2).courses.size());
        assertTrue(notifications.get(2).errorMessage.isEmpty());
        assertSame(notifications.get(2), model.getState());
    }
}
