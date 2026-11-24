package ratemycourse.usecase.compare;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ratemycourse.entity.Course;
import ratemycourse.entity.Review;
import ratemycourse.usecase.compare.ComparisonException.Reason;
import ratemycourse.usecase.gateway.CourseCatalog;
import ratemycourse.usecase.gateway.DataAccessException;
import ratemycourse.usecase.gateway.ReviewRepository;

import static org.junit.jupiter.api.Assertions.*;

class CompareCoursesInteractorTest {
    private static final String FIRST = "CSC207H1";
    private static final String SECOND = "MAT237Y1";
    private static final ComparisonInput PAIR = new ComparisonInput(FIRST, SECOND);
    private final CourseCatalog catalog = code -> Optional.of(new Course(code, "", "", null, List.of()));
    private final List<ComparisonOutput> successes = new ArrayList<>();
    private final List<ComparisonException> failures = new ArrayList<>();
    private final CompareCoursesOutputBoundary output = new CompareCoursesOutputBoundary() {
        public void presentComparison(ComparisonOutput result) {
            successes.add(result);
        }

        public void presentFailure(ComparisonException failure) {
            failures.add(failure);
        }
    };

    private ComparisonOutput success() {
        assertEquals(1, successes.size());
        assertTrue(failures.isEmpty());
        return successes.get(0);
    }

    private ComparisonException failure() {
        assertEquals(1, failures.size());
        assertTrue(successes.isEmpty());
        return failures.get(0);
    }

    @Test
    void normalizesCodesAndCalculatesEachMetricFromEachCoursesOwnReviews() {
        List<String> lookups = new ArrayList<>();
        ReviewRepository reviews = code -> {
            lookups.add(code);
            if (FIRST.equals(code)) {
                return List.of(new Review(5, 1, 4), new Review(3, 2, 4), new Review(5, 3, 1));
            }
            return List.of(new Review(1, 5, 4));
        };
        new CompareCoursesInteractor(catalog, reviews, output)
                .compare(new ComparisonInput(" cSc207h1 ", "mat237y1"));
        ComparisonOutput result = success();
        assertEquals(List.of(FIRST, SECOND), lookups);
        assertEquals(FIRST, result.first.code);
        assertEquals(SECOND, result.second.code);
        ReviewSummary left = result.first.reviews.orElseThrow();
        assertEquals(3, left.count);
        assertEquals(13.0 / 3, left.enjoyment.orElseThrow(), 1e-10);
        assertEquals(2, left.difficulty.orElseThrow());
        assertEquals(3, left.workload.orElseThrow());
        ReviewSummary right = result.second.reviews.orElseThrow();
        assertEquals(1, right.count);
        assertEquals(1, right.enjoyment.orElseThrow());
        assertEquals(5, right.difficulty.orElseThrow());
        assertEquals(4, right.workload.orElseThrow());
    }

    @Test
    void emptyReviewsHaveACountAndThreeAbsentAverages() {
        new CompareCoursesInteractor(catalog, code -> List.of(), output).compare(PAIR);
        ComparisonOutput result = success();
        for (ComparisonOutput.Column column : List.of(result.first, result.second)) {
            ReviewSummary summary = column.reviews.orElseThrow();
            assertEquals(0, summary.count);
            assertTrue(summary.enjoyment.isEmpty());
            assertTrue(summary.difficulty.isEmpty());
            assertTrue(summary.workload.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {FIRST, SECOND})
    void reviewReadFailurePreservesCatalogDataAndTheOtherSummary(String failingCode) {
        ReviewRepository reviews = code -> {
            if (failingCode.equals(code)) {
                throw new DataAccessException("Review store unavailable.");
            }
            return List.of(new Review(3, 4, 5));
        };
        new CompareCoursesInteractor(catalog, reviews, output).compare(PAIR);
        ComparisonOutput result = success();
        assertEquals(FIRST, result.first.code);
        assertEquals(SECOND, result.second.code);
        for (ComparisonOutput.Column column : List.of(result.first, result.second)) {
            if (failingCode.equals(column.code)) {
                assertTrue(column.reviews.isEmpty());
            } else {
                assertEquals(1, column.reviews.orElseThrow().count);
            }
        }
    }

    static Stream<Arguments> invalidPairs() {
        return Stream.of(Arguments.of(null, SECOND, Reason.INVALID_FIRST_CODE),
                Arguments.of("  ", SECOND, Reason.INVALID_FIRST_CODE), Arguments.of(FIRST, "MAT237", Reason.INVALID_SECOND_CODE),
                Arguments.of("CSC 207H1", SECOND, Reason.INVALID_FIRST_CODE),
                Arguments.of(FIRST, "CSC207H1/", Reason.INVALID_SECOND_CODE),
                Arguments.of(FIRST, " csc207h1 ", Reason.IDENTICAL_CODES));
    }

    @ParameterizedTest
    @MethodSource("invalidPairs")
    void rejectsInvalidSelectionsBeforeAnyLookup(String first, String second, Reason reason) {
        CompareCourses compare = new CompareCoursesInteractor(code -> fail("Unexpected catalog lookup"),
                code -> fail("Unexpected review lookup"), output);
        compare.compare(new ComparisonInput(first, second));
        assertEquals(reason, failure().reason);
    }

    @Test
    void acceptsTheCatalogsVariableLengthPrefixAndOptionalCampusDigit() {
        new CompareCoursesInteractor(catalog, code -> List.of(), output)
                .compare(new ComparisonInput("AB123H", "ABCDE456Y1"));
        ComparisonOutput result = success();
        assertEquals("AB123H", result.first.code);
        assertEquals("ABCDE456Y1", result.second.code);
    }

    @ParameterizedTest
    @ValueSource(strings = {FIRST, SECOND})
    void identifiesUnknownCourseBeforeReadingReviews(String missing) {
        CourseCatalog incomplete = code -> missing.equals(code) ? Optional.empty() : catalog.findByCode(code);
        CompareCourses compare = new CompareCoursesInteractor(incomplete, code -> fail("Unexpected review lookup"), output);
        compare.compare(PAIR);
        ComparisonException error = failure();
        assertEquals(Reason.COURSE_NOT_FOUND, error.reason);
        assertEquals(missing, error.courseCode);
    }

    @Test
    void catalogFailureKeepsItsCauseAndIdentifiesTheFailedLookup() {
        DataAccessException failure = new DataAccessException("Catalog returned HTTP 503.");
        CompareCourses compare = new CompareCoursesInteractor(code -> {
            if (SECOND.equals(code)) {
                throw failure;
            }
            return catalog.findByCode(code);
        }, code -> fail("Unexpected review lookup"), output);
        compare.compare(PAIR);
        ComparisonException error = failure();
        assertEquals(Reason.CATALOG_FAILURE, error.reason);
        assertSame(failure, error.getCause());
        assertEquals(SECOND, error.courseCode);
        assertTrue(error.getMessage().contains("503"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6})
    void rejectsOutOfRangeRatingsForEveryMetric(int invalid) {
        assertThrows(IllegalArgumentException.class, () -> new Review(invalid, 3, 4));
        assertThrows(IllegalArgumentException.class, () -> new Review(3, invalid, 4));
        assertThrows(IllegalArgumentException.class, () -> new Review(3, 4, invalid));
    }
}
