package ratemycourse.usecase.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import ratemycourse.entity.Course;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCoursesTest {
    private final Course design = new Course("CSC207H1", "Software Design", "Computer Science", 2, "Design");
    private final Course programming = new Course("CSC108H1", "Introduction to Programming",
            "Computer Science", 1, "Programming");
    private final Course algebra = new Course("MAT223H1", "Linear Algebra I", "Mathematics", 2, "Algebra");
    private final List<Course> catalogue = List.of(algebra, design, programming);
    private final SearchCourses search = new SearchCourses(() -> catalogue);

    @Test
    void normalizesCaseWhitespaceAndSpacedCourseCodes() {
        assertEquals(List.of("CSC207H1"), codes(search.execute(new SearchRequest("  cSc  207  ", "", 0))));
        assertEquals(List.of("CSC207H1"), codes(search.execute(new SearchRequest("  SOFTWARE   design ", "", 0))));
        assertEquals(List.of("CSC207H1"), codes(search.execute(new SearchRequest("ware des", "", 0))));
    }

    @Test
    void emptyQueryBrowsesWholeCatalogueInCodeOrder() {
        SearchResponse response = search.execute(new SearchRequest(" \t ", "", 0));
        assertTrue(response.isSuccess());
        assertEquals(List.of("CSC108H1", "CSC207H1", "MAT223H1"), codes(response));
        assertEquals(List.of("Computer Science", "Mathematics"), response.getDepartments());
    }

    @Test
    void combinesTextDepartmentAndYearWithAnd() {
        assertEquals(List.of("CSC207H1"), codes(search.execute(
                new SearchRequest("csc", " computer SCIENCE ", 2))));
        assertTrue(search.execute(new SearchRequest("CSC207", "Mathematics", 2)).getCourses().isEmpty());
        assertTrue(search.execute(new SearchRequest("CSC207", "Computer Science", 1)).getCourses().isEmpty());
    }

    @Test
    void emptyQueryStillAppliesEachFilter() {
        assertEquals(List.of("CSC108H1", "CSC207H1"), codes(search.execute(
                new SearchRequest("", "Computer Science", 0))));
        assertEquals(List.of("CSC207H1", "MAT223H1"), codes(search.execute(new SearchRequest("", "", 2))));
        assertEquals(List.of("MAT223H1"), codes(search.execute(new SearchRequest("", "Mathematics", 2))));
    }

    @Test
    void noMatchAndEmptyCatalogueAreSuccessfulOutcomes() {
        SearchResponse noMatch = search.execute(new SearchRequest("nonexistent", "", 0));
        assertTrue(noMatch.isSuccess());
        assertTrue(noMatch.getCourses().isEmpty());
        assertTrue(noMatch.getError().isEmpty());
        SearchResponse empty = new SearchCourses(List::of).execute(new SearchRequest("", "", 0));
        assertTrue(empty.isSuccess());
        assertTrue(empty.getCourses().isEmpty());
    }

    @Test
    void failureIsDistinctAndTheSameRequestCanRecoverOnRetry() {
        AtomicInteger loads = new AtomicInteger();
        SearchCourses recoveringSearch = new SearchCourses(() -> {
            if (loads.getAndIncrement() == 0) {
                throw new CatalogueException("Offline", new java.io.IOException("Offline"));
            }
            return catalogue;
        });
        SearchRequest request = new SearchRequest("CSC207", "", 0);
        SearchResponse failed = recoveringSearch.execute(request);
        assertFalse(failed.isSuccess());
        assertFalse(failed.getError().isBlank());
        SearchResponse retried = recoveringSearch.execute(request);
        assertTrue(retried.isSuccess());
        assertEquals(List.of("CSC207H1"), codes(retried));
    }

    @Test
    void returnsMetadataWithoutChangingSourceOrExposingMutableResults() {
        List<Course> source = new ArrayList<>(catalogue);
        SearchResponse response = new SearchCourses(() -> source).execute(new SearchRequest("CSC207", "", 0));
        CourseData selected = response.getCourses().get(0);
        assertEquals("Software Design", selected.getName());
        assertEquals("Computer Science", selected.getDepartment());
        assertEquals(2, selected.getYear());
        assertEquals("Design", selected.getDescription());
        assertEquals(catalogue, source);
        source.clear();
        assertEquals(1, response.getCourses().size());
        assertThrows(UnsupportedOperationException.class, () -> response.getCourses().clear());
        assertThrows(UnsupportedOperationException.class, () -> response.getDepartments().clear());
    }

    @Test
    void normalizingDoesNotDependOnMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(List.of("CSC207H1"), codes(search.execute(new SearchRequest("DESIGN", "", 0))));
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

    private List<String> codes(SearchResponse response) {
        return response.getCourses().stream().map(CourseData::getCode).collect(Collectors.toList());
    }
}
