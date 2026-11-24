package ratemycourse.interfaceadapter.search;

import java.util.Objects;
import ratemycourse.usecase.search.SearchResponse;

/** Latest immutable output for the preview, published by a worker and read by the UI. */
public final class SearchViewModel {
    private volatile SearchResponse response;

    void setResponse(SearchResponse response) {
        this.response = Objects.requireNonNull(response);
    }

    public SearchResponse getResponse() {
        return Objects.requireNonNull(response, "No search output has been presented yet");
    }
}
