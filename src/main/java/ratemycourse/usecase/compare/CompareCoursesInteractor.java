package ratemycourse.usecase.compare;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import ratemycourse.entity.Course;
import ratemycourse.usecase.compare.ComparisonException.Reason;
import ratemycourse.usecase.gateway.CourseCatalog;
import ratemycourse.usecase.gateway.DataAccessException;
import ratemycourse.usecase.gateway.ReviewRepository;

public final class CompareCoursesInteractor implements CompareCourses {
    private static final Pattern COURSE_CODE = Pattern.compile("[a-zA-Z]{2,5}[0-9]{3}[a-zA-Z][0-9]?");
    private final CourseCatalog catalog;
    private final ReviewRepository reviews;
    private final CompareCoursesOutputBoundary output;

    public CompareCoursesInteractor(CourseCatalog catalog, ReviewRepository reviews, CompareCoursesOutputBoundary output) {
        this.catalog = catalog;
        this.reviews = reviews;
        this.output = output;
    }

    @Override
    public void compare(ComparisonInput input) {
        try {
            String firstCode = normalize(input.firstCode, Reason.INVALID_FIRST_CODE);
            String secondCode = normalize(input.secondCode, Reason.INVALID_SECOND_CODE);
            if (firstCode.equals(secondCode)) {
                throw new ComparisonException(Reason.IDENTICAL_CODES);
            }
            Course first = lookup(firstCode);
            Course second = lookup(secondCode);
            output.presentComparison(new ComparisonOutput(column(first), column(second)));
        } catch (ComparisonException failure) {
            output.presentFailure(failure);
        }
    }

    private static String normalize(String raw, Reason invalidCode) throws ComparisonException {
        String code = raw == null ? "" : raw.strip();
        if (!COURSE_CODE.matcher(code).matches()) {
            throw new ComparisonException(invalidCode);
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private Course lookup(String code) throws ComparisonException {
        try {
            return catalog.findByCode(code).orElseThrow(() ->
                    new ComparisonException(Reason.COURSE_NOT_FOUND, code, null));
        } catch (DataAccessException ex) {
            throw new ComparisonException(Reason.CATALOG_FAILURE, code, ex);
        }
    }

    private ComparisonOutput.Column column(Course course) {
        try {
            ReviewSummary summary = ReviewSummary.from(reviews.findByCourseCode(course.code));
            return new ComparisonOutput.Column(course, Optional.of(summary));
        } catch (DataAccessException ex) {
            return new ComparisonOutput.Column(course, Optional.empty());
        }
    }
}
