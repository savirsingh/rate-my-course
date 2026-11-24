package ratemycourse.interfaceadapter.compare;

import ratemycourse.usecase.compare.CompareCourses;
import ratemycourse.usecase.compare.ComparisonInput;

public final class CompareCoursesController {
    private final CompareCourses inputBoundary;

    public CompareCoursesController(CompareCourses inputBoundary) {
        this.inputBoundary = inputBoundary;
    }

    public void compare(String firstCode, String secondCode) {
        inputBoundary.compare(new ComparisonInput(firstCode, secondCode));
    }
}
