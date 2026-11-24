package ratemycourse.usecase.search;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.CourseSearchCatalog;
import ratemycourse.usecase.gateway.CourseSearchPage;
import ratemycourse.usecase.gateway.DataAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchPaginationTest {
    private final AtomicReference<SearchResponse> output = new AtomicReference<>();

    @Test
    void ordinaryBrowsingRequestsOneServerPageAndKeepsItsTotal() {
        AtomicInteger calls = new AtomicInteger();
        SearchCourses search = new SearchCourses((query, page, size) -> {
            calls.incrementAndGet();
            assertEquals("", query);
            assertEquals(1, page);
            assertEquals(20, size);
            return new CourseSearchPage(hundred().subList(0, 20), 6497, page, size);
        }, output::set);
        search.execute(new SearchRequest("", "", 0));
        assertEquals(1, calls.get());
        assertEquals(6497, output.get().getTotal());
        assertEquals(20, output.get().getCourses().size());
        assertTrue(output.get().hasNextPage());
    }

    @Test
    void nextPageDoesNotDownloadEarlierPages() {
        SearchCourses search = new SearchCourses((query, page, size) -> {
            assertEquals("software", query);
            assertEquals(2, page);
            return new CourseSearchPage(List.of(course("CSC207H1")), 2, page, size);
        }, output::set);
        search.execute(new SearchRequest("software", "", 0, 2, 1));
        assertEquals(2, output.get().getPage());
        assertFalse(output.get().hasNextPage());
    }

    @Test
    void filtersNarrowTheServerQueryAndReuseResultsForSubsequentPages() {
        AtomicInteger calls = new AtomicInteger();
        SearchCourses search = new SearchCourses((query, page, size) -> {
            calls.incrementAndGet();
            assertEquals("csc2", query);
            assertEquals(100, size);
            return new CourseSearchPage(List.of(course("CSC207H1"), course("CSC209H1")), 2, page, size);
        }, output::set);
        search.execute(new SearchRequest("", "CSC", 2, 1, 1));
        assertEquals(2, output.get().getTotal());
        search.execute(new SearchRequest("", "CSC", 2, 2, 1));
        assertEquals("CSC209H1", output.get().getCourses().get(0).getCode());
        assertEquals(1, calls.get());
    }

    @Test
    void filteredSearchTraversesCandidatePagesAndPublishesOnce() {
        AtomicInteger presentations = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        SearchCourses search = new SearchCourses((query, page, size) -> {
            calls.incrementAndGet();
            return new CourseSearchPage(page == 1 ? hundred() : List.of(course("CSC100Y1")), 101, page, size);
        }, response -> {
            presentations.incrementAndGet();
            output.set(response);
        });
        search.execute(new SearchRequest("", "CSC", 1));
        assertEquals(101, output.get().getTotal());
        assertEquals(20, output.get().getCourses().size());
        assertEquals(2, calls.get());
        assertEquals(1, presentations.get());
    }

    @Test
    void laterPageFailureDiscardsPartialResultsAndRetryReloads() {
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        CourseSearchCatalog catalog = (query, page, size) -> {
            calls.incrementAndGet();
            if (page == 2 && fail.get()) {
                throw new DataAccessException("Unavailable");
            }
            return new CourseSearchPage(page == 1 ? hundred() : List.of(course("CSC100Y1")), 101, page, size);
        };
        SearchCourses search = new SearchCourses(catalog, output::set);
        SearchRequest request = new SearchRequest("", "CSC", 1);
        search.execute(request);
        assertFalse(output.get().isSuccess());
        assertTrue(output.get().getCourses().isEmpty());
        fail.set(false);
        search.execute(request);
        assertTrue(output.get().isSuccess());
        assertEquals(101, output.get().getTotal());
        assertEquals(4, calls.get());
    }

    @Test
    void invalidSubjectAndOversizedQueryNeverCallTheCatalog() {
        SearchCourses search = new SearchCourses((query, page, size) -> {
            throw new AssertionError("Invalid input must not reach the API");
        }, output::set);
        search.execute(new SearchRequest("", "Not a prefix", 0));
        assertFalse(output.get().isSuccess());
        search.execute(new SearchRequest("x".repeat(201), "", 0));
        assertFalse(output.get().isSuccess());
    }

    @Test
    void sharedEntityKeepsZeroCreditsAndEveryCalendarLink() {
        Course shared = new Course("APS111H1", "Engineering", "Engineering", BigDecimal.ZERO,
                List.of("https://one.example/course", "https://two.example/course"));
        CourseData data = new CourseData(shared);
        assertEquals("0", data.getCredits());
        assertEquals(shared.calendarLinks, data.getCalendarLinks());
        assertEquals("APS", data.getDepartment());
    }

    private static List<Course> hundred() {
        List<Course> courses = new ArrayList<>();
        for (int number = 100; number < 200; number++) {
            courses.add(course("CSC" + number + "H1"));
        }
        return courses;
    }

    private static Course course(String code) {
        return new Course(code, "Title", "Arts & Science", null, List.of());
    }
}
