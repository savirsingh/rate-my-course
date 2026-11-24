package ratemycourse.interfaceadapter.search;

import java.util.Objects;
import ratemycourse.usecase.search.SearchOutputBoundary;
import ratemycourse.usecase.search.SearchResponse;

/** Minimal presenter for the preview; contains no Swing or search rules. */
public final class SearchPresenter implements SearchOutputBoundary {
    private final SearchViewModel viewModel;

    public SearchPresenter(SearchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel);
    }

    @Override
    public void present(SearchResponse response) {
        viewModel.setResponse(response);
    }
}
