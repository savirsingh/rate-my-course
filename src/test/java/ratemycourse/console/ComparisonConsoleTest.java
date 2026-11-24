package ratemycourse.console;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ratemycourse.app.CompareCoursesMain;
import ratemycourse.dataaccess.DemoReviewRepository;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.CourseCatalog;
import ratemycourse.usecase.gateway.DataAccessException;
import ratemycourse.usecase.gateway.ReviewRepository;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonConsoleTest {
    private static final String LINK = "https://example.edu/calendar?page=42";
    private final CourseCatalog catalog = code -> {
        if ("CSC207H1".equals(code)) {
            return Optional.of(new Course(code, "A long course title extending across multiple lines",
                    "Test faculty", null, List.of(LINK)));
        }
        return Optional.of(new Course(code, "", "", null, List.of()));
    };

    private String run(String input, CourseCatalog courses, ReviewRepository reviews) {
        StringWriter output = new StringWriter();
        CompareCoursesMain.createConsole(courses, reviews, "DEMO reviews", new Scanner(input),
                new PrintWriter(output)).run();
        return output.toString();
    }

    @Test
    void rendersAlignedMetadataUnbrokenLinksDemoAveragesAndEmptyReviews() {
        String text = run("CSC207H1\nCSC148H1\nn\n", catalog, new DemoReviewRepository());
        assertTrue(text.contains("Review source: DEMO reviews"));
        assertTrue(text.contains("[1] CSC207H1  " + LINK));
        List<String> rows = text.lines().filter(line -> line.startsWith("Course code") || line.startsWith("Title")
                || line.startsWith("               |") || line.startsWith("Avg ") || line.startsWith("Review count")
                || line.startsWith("Credits")).collect(Collectors.toList());
        String title = rows.stream().filter(line -> line.startsWith("Title") || line.startsWith("               |"))
                .map(line -> line.split("\\|", -1)[1].strip()).collect(Collectors.joining(" "));
        assertEquals("A long course title extending across multiple lines", title);
        for (String row : rows) {
            assertEquals(15, row.indexOf('|'));
            assertEquals(46, row.lastIndexOf('|'));
        }
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("Title") && row.endsWith("Not provided")));
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("Credits") && row.endsWith("Not provided")));
        assertTrue(rows.stream().anyMatch(row -> row.startsWith("Review count")
                && row.contains("| 3 ") && row.endsWith("| 0")));
        assertTrue(rows.stream().anyMatch(row -> row.contains("4.33") && row.endsWith("| N/A")));
        assertEquals(3, rows.stream().filter(row -> row.endsWith("| N/A")).count());
        assertTrue(text.contains("CSC148H1 has no reviews."));
    }

    @Test
    void failedReviewsHaveUnavailableCountAndRatingsBesideKnownEmptyReviews() {
        ReviewRepository reviews = code -> {
            if ("CSC207H1".equals(code)) {
                throw new DataAccessException("Review service offline.");
            }
            return List.of();
        };
        String text = run("CSC207H1\nCSC148H1\nn\n", catalog, reviews);
        assertEquals(4, text.lines().filter(line -> line.contains("Unavailable")).count());
        assertTrue(text.lines().anyMatch(line -> line.startsWith("Review count") && line.endsWith("| 0")));
        assertTrue(text.contains("Could not load reviews for CSC207H1."));
        assertTrue(text.contains("CSC148H1 has no reviews."));
        assertFalse(text.contains("CSC207H1 has no reviews."));
    }

    @Test
    void compositionRootWiresCatalogFailureThenSuccessThroughTheSameSession() {
        CourseCatalog intermittent = code -> {
            if ("BAD999H1".equals(code)) {
                throw new DataAccessException("Catalog offline.");
            }
            return catalog.findByCode(code);
        };
        String text = run("BAD999H1\nCSC207H1\ny\nMAT237Y1\nCSC207H1\nn\n", intermittent, new DemoReviewRepository());
        assertTrue(text.contains("Could not look up BAD999H1. Catalog offline."));
        assertEquals(1, text.lines().filter(line -> line.startsWith("Course code")).count());
        assertTrue(text.lines().anyMatch(line -> line.startsWith("Course code") && line.endsWith("CSC207H1")));
        assertTrue(text.lines().anyMatch(line -> line.startsWith("Review count") && line.contains("| 2 ") && line.endsWith("| 3")));
    }

    @Test
    void canRecoverFromAnErrorRepeatAComparisonAndExit() {
        String text = run("CSC207H1\ncsc207h1\nmaybe\ny\nCSC207H1\nMAT237Y1\nyes\nMAT237Y1\nCSC207H1\nno\n",
                catalog, new DemoReviewRepository());
        assertTrue(text.contains("Choose two different courses."));
        assertTrue(text.contains("Enter y or n."));
        assertEquals(2, text.lines().filter(line -> line.startsWith("Course code")).count());
        assertTrue(text.stripTrailing().endsWith("Goodbye."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "q\n", "CSC207H1\n", "CSC207H1\nexit\n"})
    void quitOrEndOfInputBeforeAPairAvoidsLookups(String input) {
        assertTrue(run(input, code -> fail("Unexpected catalog lookup"), code -> fail("Unexpected review lookup"))
                .stripTrailing().endsWith("Goodbye."));
    }
}
