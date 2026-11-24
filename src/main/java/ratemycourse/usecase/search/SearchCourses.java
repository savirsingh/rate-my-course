package ratemycourse.usecase.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.CourseSearchCatalog;
import ratemycourse.usecase.gateway.CourseSearchPage;
import ratemycourse.usecase.gateway.DataAccessException;

/** UC1 search rules. Text matching and ordinary pagination are delegated to the shared API gateway. */
public final class SearchCourses implements SearchInputBoundary {
    private final CourseSearchCatalog catalog;
    private final SearchOutputBoundary presenter;
    private FilteredResult cached;

    public SearchCourses(CourseSearchCatalog catalog, SearchOutputBoundary presenter) {
        this.catalog = Objects.requireNonNull(catalog);
        this.presenter = Objects.requireNonNull(presenter);
    }

    @Override
    public synchronized void execute(SearchRequest request) {
        Objects.requireNonNull(request);
        SearchResponse response;
        if (request.getQuery().length() > 200 || (!request.getDepartment().isEmpty()
                && !request.getDepartment().matches("[a-z]{2,5}"))) {
            presenter.present(SearchResponse.failure("Use a subject prefix such as CSC and at most 200 query characters.",
                    request));
            return;
        }
        try {
            if (request.getDepartment().isEmpty() && request.getYear() == 0) {
                CourseSearchPage page = catalog.search(request.getQuery(), request.getPage(), request.getPageSize());
                List<CourseData> courses = page.courses.stream().map(CourseData::new).collect(Collectors.toList());
                response = SearchResponse.success(courses, page.total, request);
            }
            else {
                response = filteredPage(request);
            }
        }
        catch (DataAccessException exception) {
            cached = null;
            response = SearchResponse.failure("Could not load the course catalog. " + exception.getMessage(), request);
        }
        presenter.present(response);
    }

    private SearchResponse filteredPage(SearchRequest request) throws DataAccessException {
        // The API has no subject/year parameters. Narrow its query first, then apply these local filters.
        if (request.getPage() == 1 || cached == null || !cached.matches(request)) {
            String query = request.getQuery();
            if (query.isEmpty()) {
                query = request.getDepartment() + (request.getYear() == 0 ? "" : request.getYear());
            }
            List<CourseData> matches = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            int expectedTotal = -1;
            int loaded = 0;
            int pageNumber = 1;
            do {
                CourseSearchPage page = catalog.search(query, pageNumber, 100);
                if ((expectedTotal != -1 && expectedTotal != page.total) || page.total > 100000
                        || page.total < 0 || (page.courses.isEmpty() && loaded < page.total)) {
                    throw new DataAccessException("The catalog changed or returned an incomplete search. Please retry.");
                }
                expectedTotal = page.total;
                for (Course course : page.courses) {
                    if (!codes.add(course.code)) {
                        throw new DataAccessException("The catalog returned duplicate courses. Please retry.");
                    }
                    CourseData data = new CourseData(course);
                    if ((request.getDepartment().isEmpty()
                            || data.getDepartment().equalsIgnoreCase(request.getDepartment()))
                            && (request.getYear() == 0 || data.getYear() == request.getYear())) {
                        matches.add(data);
                    }
                }
                loaded += page.courses.size();
                pageNumber++;
            } while (loaded < expectedTotal);
            cached = new FilteredResult(request, matches);
        }
        long offset = ((long) request.getPage() - 1) * request.getPageSize();
        int from = (int) Math.min(offset, cached.courses.size());
        int to = Math.min(from + request.getPageSize(), cached.courses.size());
        return SearchResponse.success(cached.courses.subList(from, to), cached.courses.size(), request);
    }

    private static final class FilteredResult {
        private final SearchRequest request;
        private final List<CourseData> courses;

        private FilteredResult(SearchRequest request, List<CourseData> courses) {
            this.request = request;
            this.courses = List.copyOf(courses);
        }

        private boolean matches(SearchRequest next) {
            return request.getQuery().equals(next.getQuery())
                    && request.getDepartment().equals(next.getDepartment()) && request.getYear() == next.getYear();
        }
    }
}
