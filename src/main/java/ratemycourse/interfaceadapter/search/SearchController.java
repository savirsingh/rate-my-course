package ratemycourse.interfaceadapter.search;

import ratemycourse.usecase.search.SearchInputBoundary;
import ratemycourse.usecase.search.SearchRequest;

/** Converts view input to use-case input, following UC3's controller convention. */
public final class SearchController {
    private final SearchInputBoundary input;
    private SearchRequest current;

    public SearchController(SearchInputBoundary input) {
        this.input = input;
    }

    public void search(String query, String subject, int year) {
        current = new SearchRequest(query, subject, year);
        input.execute(current);
    }

    public void page(int page) {
        if (current == null) {
            throw new IllegalStateException("Submit a search before changing pages");
        }
        current = current.withPage(page);
        input.execute(current);
    }
}
