package ratemycourse.dataaccess;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ratemycourse.entity.Course;
import ratemycourse.usecase.search.CatalogueException;
import ratemycourse.usecase.search.SearchCourses;
import ratemycourse.usecase.search.SearchRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsvCourseCatalogueTest {
    private static final String HEADER = "code\tname\tdepartment\tyear\tdescription\n";
    private static final String ROW = "CSC207H1\tSoftware Design\tComputer Science\t2\tDesign\n";

    @Test
    void loadsBundledDemoThroughSearchBoundary() {
        TsvCourseCatalogue adapter = new TsvCourseCatalogue(
                () -> getClass().getResourceAsStream("/demo-courses.tsv"));
        assertEquals("CSC207H1", new SearchCourses(adapter).execute(
                new SearchRequest("csc 207", "", 0)).getCourses().get(0).getCode());
    }

    @Test
    void readsUtf8AndReturnsImmutableCatalogue() throws CatalogueException {
        List<Course> courses = adapter(HEADER + ROW.replace("Design", "Désign")).loadCourses();
        assertEquals("Software Désign", courses.get(0).getName());
        assertThrows(UnsupportedOperationException.class, courses::clear);
    }

    @Test
    void headerOnlyCatalogueIsAValidEmptyCatalogue() throws CatalogueException {
        assertTrue(adapter(HEADER).loadCourses().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "", "incorrect header\n", "code\tname\tdepartment\tyear\tdescription\ninvalid row",
        "code\tname\tdepartment\tyear\tdescription\nCSC207\tDesign\tCS\tnot-a-year\tDesign",
        "code\tname\tdepartment\tyear\tdescription\nCSC207\tDesign\tCS\t5\tDesign",
        "code\tname\tdepartment\tyear\tdescription\nCSC207\t\tCS\t2\tDesign"
    })
    void malformedDataFailsRatherThanAppearingEmpty(String data) {
        assertThrows(CatalogueException.class, () -> adapter(data).loadCourses());
    }

    @Test
    void rejectsDuplicateCodesAndPartiallyValidCatalogues() {
        assertThrows(CatalogueException.class,
                () -> adapter(HEADER + ROW + ROW.toLowerCase(java.util.Locale.ROOT)).loadCourses());
        assertThrows(CatalogueException.class, () -> adapter(HEADER + ROW + "broken row").loadCourses());
    }

    @Test
    void missingAndUnreadableSourcesAreFailures() {
        assertThrows(CatalogueException.class, () -> new TsvCourseCatalogue(() -> null).loadCourses());
        assertThrows(CatalogueException.class, () -> new TsvCourseCatalogue(() -> {
            throw new IOException("Unavailable");
        }).loadCourses());
    }

    @Test
    void searchLeavesFileBytesUnchangedAndReloadsOnRetry(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("courses.tsv");
        byte[] original = (HEADER + ROW).getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        SearchCourses search = new SearchCourses(new TsvCourseCatalogue(() -> Files.newInputStream(file)));
        assertTrue(search.execute(new SearchRequest("CSC207", "", 0)).isSuccess());
        assertArrayEquals(original, Files.readAllBytes(file));
        Files.delete(file);
        assertTrue(!search.execute(new SearchRequest("", "", 0)).isSuccess());
        Files.write(file, original);
        assertEquals(1, search.execute(new SearchRequest("", "", 0)).getCourses().size());
    }

    private TsvCourseCatalogue adapter(String data) {
        return new TsvCourseCatalogue(() -> new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    }
}
