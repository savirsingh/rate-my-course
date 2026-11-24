package ratemycourse.usecase.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.CourseSearchPage;
import ratemycourse.usecase.gateway.DataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCoursesTest {
    private final Course design = new Course("CSC207H1", "Software Design", "Arts & Science", null, List.of());
    private final Course programming = new Course("CSC108H1", "Introduction to Programming",
            "Arts & Science", null, List.of());
    private final Course algebra = new Course("MAT223H1", "Linear Algebra I", "Arts & Science", null, List.of());
    private final List<Course> catalogue = List.of(algebra, design, programming);
    private final List<SearchResponse> presented = new ArrayList<>();
    private final SearchCourses search = new SearchCourses(this::fixture, presented::add);

    @Test
    void normalizesCaseWhitespaceAndSpacedCourseCodes() {
        assertEquals(List.of("CSC207H1"), codes(executeAndCapture(search, new SearchRequest("  cSc  207  ", "", 0))));
        assertEquals(List.of("CSC207H1"), codes(executeAndCapture(search,
                new SearchRequest("  SOFTWARE   design ", "", 0))));
        assertEquals(List.of("CSC207H1"), codes(executeAndCapture(search, new SearchRequest("ware des", "", 0))));
    }

    @Test
    void emptyQueryBrowsesWholeCatalogueInCodeOrder() {
        SearchResponse response = executeAndCapture(search, new SearchRequest(" \t ", "", 0));
        assertTrue(response.isSuccess());
        assertEquals(List.of("CSC108H1", "CSC207H1", "MAT223H1"), codes(response));
        assertEquals(3, response.getTotal());
    }

    @Test
    void combinesTextDepartmentAndYearWithAnd() {
        assertEquals(List.of("CSC207H1"), codes(executeAndCapture(search,
                new SearchRequest("csc", " cSc ", 2))));
        assertTrue(executeAndCapture(search, new SearchRequest("CSC207", "MAT", 2)).getCourses().isEmpty());
        assertTrue(executeAndCapture(search,
                new SearchRequest("CSC207", "CSC", 1)).getCourses().isEmpty());
    }

    @Test
    void emptyQueryStillAppliesEachFilter() {
        assertEquals(List.of("CSC108H1", "CSC207H1"), codes(executeAndCapture(search,
                new SearchRequest("", "CSC", 0))));
        assertEquals(List.of("CSC207H1", "MAT223H1"), codes(executeAndCapture(search, new SearchRequest("", "", 2))));
        assertEquals(List.of("MAT223H1"), codes(executeAndCapture(search, new SearchRequest("", "MAT", 2))));
    }

    @Test
    void noMatchAndEmptyCatalogueAreSuccessfulOutcomes() {
        SearchResponse noMatch = executeAndCapture(search, new SearchRequest("nonexistent", "", 0));
        assertTrue(noMatch.isSuccess());
        assertTrue(noMatch.getCourses().isEmpty());
        assertTrue(noMatch.getError().isEmpty());
        SearchResponse empty = executeAndCapture(new SearchCourses((query, page, size) -> new CourseSearchPage(List.of(), 0, page, size),
                presented::add),
                new SearchRequest("", "", 0));
        assertTrue(empty.isSuccess());
        assertTrue(empty.getCourses().isEmpty());
    }

    @Test
    void failureIsDistinctAndTheSameRequestCanRecoverOnRetry() {
        AtomicInteger loads = new AtomicInteger();
        SearchCourses recoveringSearch = new SearchCourses((query, page, size) -> {
            if (loads.getAndIncrement() == 0) {
                throw new DataAccessException("Offline", new java.io.IOException("Offline"));
            }
            return fixture(query, page, size);
        }, presented::add);
        SearchRequest request = new SearchRequest("CSC207", "", 0);
        SearchResponse failed = executeAndCapture(recoveringSearch, request);
        assertFalse(failed.isSuccess());
        assertFalse(failed.getError().isBlank());
        SearchResponse retried = executeAndCapture(recoveringSearch, request);
        assertTrue(retried.isSuccess());
        assertEquals(List.of("CSC207H1"), codes(retried));
    }

    @Test
    void presentsMetadataWithoutChangingSourceOrExposingMutableResults() {
        List<Course> source = new ArrayList<>(catalogue);
        SearchResponse response = executeAndCapture(new SearchCourses((query, page, size) ->
                new CourseSearchPage(source, source.size(), page, size),
                presented::add),
                new SearchRequest("CSC207", "", 0));
        CourseData selected = response.getCourses().stream().filter(course -> course.getCode().equals("CSC207H1"))
                .findFirst().orElseThrow();
        assertEquals("Software Design", selected.getName());
        assertEquals("CSC", selected.getDepartment());
        assertEquals(2, selected.getYear());
        assertTrue(selected.getDescription().contains("not provided"));
        assertEquals("Arts & Science", selected.getFaculty());
        assertEquals("Not provided", selected.getCredits());
        assertEquals(catalogue, source);
        source.clear();
        assertEquals(3, response.getCourses().size());
        assertThrows(UnsupportedOperationException.class, () -> response.getCourses().clear());
        assertThrows(UnsupportedOperationException.class, () -> selected.getCalendarLinks().clear());
    }

    @Test
    void normalizingDoesNotDependOnMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(List.of("CSC207H1"), codes(executeAndCapture(search, new SearchRequest("DESIGN", "", 0))));
        }
        finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsInvalidYearFilters() {
        assertThrows(IllegalArgumentException.class, () -> new SearchRequest("", "", 5));
        assertThrows(IllegalArgumentException.class, () -> new SearchRequest("", "", -1));
    }

    private CourseSearchPage fixture(String query, int page, int pageSize) {
        List<Course> matches = catalogue.stream().filter(course -> course.code.toLowerCase(Locale.ROOT).contains(query)
                || SearchRequest.normalize(course.title).contains(query)).sorted(Comparator.comparing(course -> course.code))
                .collect(Collectors.toList());
        int from = Math.min((page - 1) * pageSize, matches.size());
        return new CourseSearchPage(matches.subList(from, Math.min(from + pageSize, matches.size())),
                matches.size(), page, pageSize);
    }

    private SearchResponse executeAndCapture(SearchInputBoundary input, SearchRequest request) {
        int previousOutputs = presented.size();
        input.execute(request);
        assertEquals(previousOutputs + 1, presented.size(), "Each request must present exactly one response");
        return presented.get(previousOutputs);
    }

    private List<String> codes(SearchResponse response) {
        return response.getCourses().stream().map(CourseData::getCode).collect(Collectors.toList());
    }
}
