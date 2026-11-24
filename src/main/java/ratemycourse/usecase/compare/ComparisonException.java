package ratemycourse.usecase.compare;

import ratemycourse.usecase.gateway.DataAccessException;

/** A selection or catalog error prevented a comparison. */
public final class ComparisonException extends Exception {
    public enum Reason {
        INVALID_FIRST_CODE, INVALID_SECOND_CODE, IDENTICAL_CODES, COURSE_NOT_FOUND, CATALOG_FAILURE
    }

    public final Reason reason;
    public final String courseCode;

    public ComparisonException(Reason reason) {
        this(reason, "", null);
    }

    public ComparisonException(Reason reason, String courseCode, DataAccessException cause) {
        super(cause == null ? null : cause.getMessage(), cause);
        this.reason = reason;
        this.courseCode = courseCode;
    }
}
