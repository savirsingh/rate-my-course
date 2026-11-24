package ratemycourse.interfaceadapter.search;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ratemycourse.entity.Course;
import ratemycourse.usecase.search.CatalogueException;
import ratemycourse.usecase.search.SearchCourses;
import ratemycourse.usecase.search.SearchInputBoundary;
import ratemycourse.usecase.search.SearchRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPresentationTest {
    @Test
    void outputBoundaryReplacesFailureWithRetryResultsAndClearsOldResultsOnLaterSearches() {
        SearchViewModel viewModel = new SearchViewModel();
        AtomicBoolean unavailable = new AtomicBoolean(true);
        SearchInputBoundary search = new SearchCourses(() -> {
            if (unavailable.get()) {
                throw new CatalogueException("Offline", new IOException("Offline"));
            }
            return List.of(new Course("CSC207H1", "Software Design", "CSC", 2, "Design"));
        }, new SearchPresenter(viewModel));
        SearchRequest request = new SearchRequest("CSC207", "", 0);

        search.execute(request);
        assertFalse(viewModel.getResponse().isSuccess());
        assertFalse(viewModel.getResponse().getError().isEmpty());

        unavailable.set(false);
        search.execute(request);
        assertTrue(viewModel.getResponse().isSuccess());
        assertEquals("CSC207H1", viewModel.getResponse().getCourses().get(0).getCode());
        assertTrue(viewModel.getResponse().getError().isEmpty());

        search.execute(new SearchRequest("nonexistent", "", 0));
        assertTrue(viewModel.getResponse().isSuccess());
        assertTrue(viewModel.getResponse().getCourses().isEmpty());

        unavailable.set(true);
        search.execute(request);
        assertFalse(viewModel.getResponse().isSuccess());
        assertTrue(viewModel.getResponse().getCourses().isEmpty());
    }
}
