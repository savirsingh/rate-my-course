package ratemycourse.interfaceadapter.search;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import ratemycourse.usecase.search.CourseData;
import ratemycourse.usecase.search.SearchOutputBoundary;
import ratemycourse.usecase.search.SearchResponse;

/** Converts use-case output to display state; never calls Swing or the catalog. */
public final class SearchPresenter implements SearchOutputBoundary {
    private final SearchViewModel viewModel;

    public SearchPresenter(SearchViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel);
    }

    @Override
    public void present(SearchResponse response) {
        List<SearchViewModel.Row> rows = response.getCourses().stream().map(SearchPresenter::row)
                .collect(Collectors.toList());
        String status = response.getTotal() + (response.getTotal() == 1 ? " matching course" : " matching courses");
        if (response.getTotal() == 0) {
            status = "No matching courses. Change your search or filters.";
        }
        else if (rows.isEmpty()) {
            status += " — no results on page " + response.getPage() + ". Return to a previous page.";
        }
        else {
            long start = ((long) response.getPage() - 1) * response.getPageSize() + 1;
            status += " — page " + response.getPage() + ", showing " + start + "–"
                    + (start + rows.size() - 1);
        }
        if (!response.isSuccess()) {
            status = response.getError();
        }
        viewModel.show(new SearchViewModel.State(rows, status, response.getError(), response.getPage(),
                response.isSuccess() && response.hasNextPage()));
    }

    private static SearchViewModel.Row row(CourseData course) {
        String label = course.getCode() + " — " + metadata(course.getName());
        String level = course.getYear() == 0 ? "Not provided" : Integer.toString(course.getYear());
        String details = label + "\n\nFaculty: " + metadata(course.getFaculty())
                + "\nSubject: " + metadata(course.getDepartment()) + " | Code level: " + level
                + "\nCredits: " + course.getCredits() + "\n\n" + course.getDescription()
                + "\nOfficial listings:\n" + (course.getCalendarLinks().isEmpty() ? "Not provided"
                : String.join("\n", course.getCalendarLinks()))
                + "\n\nReviews are not available in this preview.\n"
                + "Reviewing, saving, and comparing are not available yet.";
        return new SearchViewModel.Row(course.getCode(), label, details);
    }

    private static String metadata(String value) {
        return value.isBlank() ? "Not provided" : value;
    }
}
