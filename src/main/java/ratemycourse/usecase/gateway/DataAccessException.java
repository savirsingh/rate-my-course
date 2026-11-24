package ratemycourse.usecase.gateway;

/** The data source could not complete a lookup. The message must be safe to show a user. */
public final class DataAccessException extends Exception {
    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
