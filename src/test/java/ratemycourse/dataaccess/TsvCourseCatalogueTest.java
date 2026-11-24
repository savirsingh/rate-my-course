package ratemycourse.dataaccess;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.DataAccessException;
import ratemycourse.usecase.search.SearchCourses;
import ratemycourse.usecase.search.SearchRequest;
import ratemycourse.usecase.search.SearchResponse;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsvCourseCatalogueTest {
    private static final String HEADER = "code\tname\tfaculty\tcredits\tsource_url\n";
    private static final String ROW = "CSC207H1\tSoftware Design\tArts & Science\t\t\n";

    @Test
    void loadsBundledDemoThroughSearchBoundary() {
        TsvCourseCatalogue adapter = new TsvCourseCatalogue(
                () -> getClass().getResourceAsStream("/demo-courses.tsv"));
        AtomicReference<SearchResponse> output = new AtomicReference<>();
        new SearchCourses(adapter, output::set).execute(new SearchRequest("csc 207", "", 0));
        assertEquals("CSC207H1", output.get().getCourses().get(0).getCode());
    }

    @Test
    void readsUtf8AndReturnsImmutableCatalogue() throws DataAccessException {
        List<Course> courses = adapter(HEADER + ROW.replace("Design", "Désign")).loadCourses();
        assertEquals("Software Désign", courses.get(0).title);
        assertThrows(UnsupportedOperationException.class, courses::clear);
    }

    @Test
    void headerOnlyCatalogueIsAValidEmptyCatalogue() throws DataAccessException {
        assertTrue(adapter(HEADER).loadCourses().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "incorrect header\n", "code\tname\tfaculty\tcredits\tsource_url\ninvalid row",
        "code\tname\tfaculty\tcredits\tsource_url\nCSC207H1\tDesign\tArts & Science\tnot-a-credit\t",
        "code\tname\tfaculty\tcredits\tsource_url\nBAD-CODE\tDesign\tArts & Science\t0.5\t",
        "code\tname\tfaculty\tcredits\tsource_url\nCSC207H1\t\tArts & Science\t0.5\t"
    })
    void malformedDataFailsRatherThanAppearingEmpty(String data) {
        assertThrows(DataAccessException.class, () -> adapter(data).loadCourses());
    }

    @Test
    void rejectsDuplicateCodesAndPartiallyValidCatalogues() {
        assertThrows(DataAccessException.class,
                () -> adapter(HEADER + ROW + ROW.toLowerCase(java.util.Locale.ROOT)).loadCourses());
        assertThrows(DataAccessException.class, () -> adapter(HEADER + ROW + "broken row").loadCourses());
    }

    @Test
    void missingAndUnreadableSourcesAreFailures() {
        assertThrows(DataAccessException.class, () -> new TsvCourseCatalogue(() -> null).loadCourses());
        assertThrows(DataAccessException.class, () -> new TsvCourseCatalogue(() -> {
            throw new IOException("Unavailable");
        }).loadCourses());
    }

    @Test
    void searchLeavesFileBytesUnchangedAndReloadsOnRetry(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("courses.tsv");
        byte[] original = (HEADER + ROW).getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        AtomicReference<SearchResponse> output = new AtomicReference<>();
        SearchCourses search = new SearchCourses(new TsvCourseCatalogue(() -> Files.newInputStream(file)), output::set);
        search.execute(new SearchRequest("CSC207", "", 0));
        assertTrue(output.get().isSuccess());
        assertArrayEquals(original, Files.readAllBytes(file));
        Files.delete(file);
        search.execute(new SearchRequest("", "", 0));
        assertTrue(!output.get().isSuccess());
        Files.write(file, original);
        search.execute(new SearchRequest("", "", 0));
        assertEquals(1, output.get().getCourses().size());
    }

    private TsvCourseCatalogue adapter(String data) {
        return new TsvCourseCatalogue(() -> new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    }
}
