package ratemycourse.usecase.search;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import ratemycourse.entity.Course;

/** UC1 search rules. No UI, file, network, or write operations belong here. */
public final class SearchCourses implements SearchInputBoundary {
    private final CourseCatalogue catalogue;
    private final SearchOutputBoundary presenter;

    public SearchCourses(CourseCatalogue catalogue, SearchOutputBoundary presenter) {
        this.catalogue = Objects.requireNonNull(catalogue);
        this.presenter = Objects.requireNonNull(presenter);
    }

    @Override
    public void execute(SearchRequest request) {
        Objects.requireNonNull(request);
        SearchResponse response;
        try {
            List<Course> courses = catalogue.loadCourses();
            List<String> departments = courses.stream().map(Course::getDepartment)
                    .distinct().sorted().collect(Collectors.toList());
            List<CourseData> matches = courses.stream()
                    .filter(course -> matches(course, request))
                    .sorted(Comparator.comparing(Course::getCode))
                    .map(CourseData::new).collect(Collectors.toList());
            response = SearchResponse.success(matches, departments);
        }
        catch (CatalogueException exception) {
            response = SearchResponse.failure();
        }
        presenter.present(response);
    }

    private boolean matches(Course course, SearchRequest request) {
        String query = request.getQuery();
        // Accept both CSC207 and CSC 207 while preserving word boundaries in title searches.
        boolean textMatches = SearchRequest.normalize(course.getCode()).replace(" ", "")
                .contains(query.replace(" ", ""))
                || SearchRequest.normalize(course.getName()).contains(query);
        boolean departmentMatches = request.getDepartment().isEmpty()
                || SearchRequest.normalize(course.getDepartment()).equals(request.getDepartment());
        boolean yearMatches = request.getYear() == 0 || course.getYear() == request.getYear();
        return textMatches && departmentMatches && yearMatches;
    }
}
