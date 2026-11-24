package ratemycourse.usecase.compare;

/** A comparison produces exactly one of these outcomes. Review failures belong to the affected column. */
public interface CompareCoursesOutputBoundary {
    void presentComparison(ComparisonOutput comparison);

    void presentFailure(ComparisonException failure);
}
