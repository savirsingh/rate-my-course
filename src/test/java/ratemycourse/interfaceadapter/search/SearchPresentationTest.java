package ratemycourse.interfaceadapter.search;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.DataAccessException;
import ratemycourse.usecase.gateway.CourseSearchPage;
import ratemycourse.usecase.search.SearchCourses;
import ratemycourse.usecase.search.SearchInputBoundary;
import ratemycourse.usecase.search.SearchRequest;
import ratemycourse.usecase.search.SearchResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPresentationTest {
    @Test
    void emptyPageRetainsTheTotalWithoutDisplayingAnInvalidRange() {
        SearchViewModel model = new SearchViewModel();
        new SearchPresenter(model).present(SearchResponse.success(List.of(), 20,
                new SearchRequest("", "", 0, 2, 20)));
        assertTrue(model.getState().status.contains("20 matching courses"));
        assertTrue(model.getState().status.contains("no results on page 2"));
        assertFalse(model.getState().status.contains("21–20"));
    }

    @Test
    void outputBoundaryReplacesFailureWithRetryResultsAndClearsOldResultsOnLaterSearches() {
        SearchViewModel viewModel = new SearchViewModel();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        SearchInputBoundary search = new SearchCourses((query, page, pageSize) -> {
            if (unavailable.get()) {
                throw new DataAccessException("Offline", new IOException("Offline"));
            }
            List<Course> courses = query.equals("nonexistent") ? List.of()
                    : List.of(new Course("CSC207H1", "Software Design", "Arts & Science", null, List.of()));
            return new CourseSearchPage(courses, courses.size(), page, pageSize);
        }, new SearchPresenter(viewModel));
        SearchRequest request = new SearchRequest("CSC207", "", 0);

        search.execute(request);
        assertFalse(viewModel.getState().error.isEmpty());
        assertFalse(viewModel.getState().error.isEmpty());

        unavailable.set(false);
        search.execute(request);
        assertTrue(viewModel.getState().error.isEmpty());
        assertEquals("CSC207H1", viewModel.getState().courses.get(0).code);
        assertTrue(viewModel.getState().error.isEmpty());

        search.execute(new SearchRequest("nonexistent", "", 0));
        assertTrue(viewModel.getState().error.isEmpty());
        assertTrue(viewModel.getState().courses.isEmpty());

        unavailable.set(true);
        search.execute(request);
        assertFalse(viewModel.getState().error.isEmpty());
        assertTrue(viewModel.getState().courses.isEmpty());
    }
}
