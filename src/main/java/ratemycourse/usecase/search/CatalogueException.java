package ratemycourse.usecase.search;

/** A catalogue could not be loaded; this is distinct from zero matches. */
public final class CatalogueException extends Exception {
    public CatalogueException(String message, Throwable cause) {
        super(message, cause);
    }
}
