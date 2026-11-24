package ratemycourse.usecase.search;

/** Presenter port owned by the search use case, independent of the UI. */
public interface SearchOutputBoundary {
    void present(SearchResponse response);
}
