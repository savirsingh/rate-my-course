package ratemycourse.interfaceadapter.compare;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.OptionalDouble;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel.Column;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel.ReviewDisplay;
import ratemycourse.usecase.compare.CompareCoursesOutputBoundary;
import ratemycourse.usecase.compare.ComparisonException;
import ratemycourse.usecase.compare.ComparisonOutput;
import ratemycourse.usecase.compare.ReviewSummary;

import static ratemycourse.interfaceadapter.compare.CompareCoursesViewModel.MISSING_METADATA;

public final class CompareCoursesPresenter implements CompareCoursesOutputBoundary {
    private static final String UNAVAILABLE = "Unavailable";
    private final CompareCoursesViewModel viewModel;

    public CompareCoursesPresenter(CompareCoursesViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @Override
    public void presentComparison(ComparisonOutput comparison) {
        viewModel.showComparison(column(comparison.first), column(comparison.second));
    }

    private static Column column(ComparisonOutput.Column course) {
        return new Column(course.code, metadata(course.title), metadata(course.faculty),
                course.credits.map(BigDecimal::toPlainString).orElse(MISSING_METADATA),
                course.calendarLinks, reviews(course));
    }

    private static String metadata(String value) {
        return value.isBlank() ? MISSING_METADATA : value;
    }

    private static ReviewDisplay reviews(ComparisonOutput.Column course) {
        if (course.reviews.isEmpty()) {
            return new ReviewDisplay(UNAVAILABLE, UNAVAILABLE, UNAVAILABLE, UNAVAILABLE,
                    "Could not load reviews for " + course.code + ".");
        }
        ReviewSummary summary = course.reviews.get();
        String note = summary.count == 0 ? course.code + " has no reviews." : "";
        return new ReviewDisplay(Integer.toString(summary.count), average(summary.enjoyment),
                average(summary.difficulty), average(summary.workload), note);
    }

    private static String average(OptionalDouble value) {
        return value.isPresent() ? String.format(Locale.ROOT, "%.2f", value.getAsDouble()) : "N/A";
    }

    @Override
    public void presentFailure(ComparisonException failure) {
        String message;
        switch (failure.reason) {
            case INVALID_FIRST_CODE:
            case INVALID_SECOND_CODE:
                String position = failure.reason == ComparisonException.Reason.INVALID_FIRST_CODE ? "first" : "second";
                message = "Enter a full course code for the " + position + " course (for example, CSC207H1).";
                break;
            case IDENTICAL_CODES:
                message = "Choose two different courses.";
                break;
            case COURSE_NOT_FOUND:
                message = failure.courseCode + " was not found in the course catalog.";
                break;
            case CATALOG_FAILURE:
                message = "Could not look up " + failure.courseCode + ". " + failure.getMessage();
                break;
            default:
                throw new IllegalStateException("Unhandled comparison failure: " + failure.reason);
        }
        viewModel.showError(message);
    }
}
