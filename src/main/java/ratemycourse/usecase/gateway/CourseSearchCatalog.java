package ratemycourse.usecase.gateway;

/** Read-only, server-side search without changing the exact-lookup contract used by UC3. */
public interface CourseSearchCatalog {
    CourseSearchPage search(String query, int page, int pageSize) throws DataAccessException;
}
