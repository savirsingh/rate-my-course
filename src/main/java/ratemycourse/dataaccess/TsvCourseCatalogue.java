package ratemycourse.dataaccess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import ratemycourse.entity.Course;
import ratemycourse.usecase.search.CatalogueException;
import ratemycourse.usecase.search.CourseCatalogue;

/** Loads a UTF-8 tab-separated catalogue atomically; malformed data is never an empty success. */
public final class TsvCourseCatalogue implements CourseCatalogue {
    private static final String HEADER = "code\tname\tdepartment\tyear\tdescription";
    private final Source source;

    @FunctionalInterface
    public interface Source {
        InputStream open() throws IOException;
    }

    public TsvCourseCatalogue(Source source) {
        this.source = Objects.requireNonNull(source);
    }

    @Override
    public List<Course> loadCourses() throws CatalogueException {
        try (InputStream stream = source.open()) {
            if (stream == null) {
                throw new IOException("Catalogue resource is missing");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return readCourses(reader);
            }
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new CatalogueException("Catalogue could not be read", exception);
        }
    }

    private List<Course> readCourses(BufferedReader reader) throws IOException {
        if (!HEADER.equals(reader.readLine())) {
            throw new IOException("Invalid catalogue header");
        }
        List<Course> courses = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        String line = reader.readLine();
        while (line != null) {
            String[] fields = line.split("\t", -1);
            if (fields.length != 5) {
                throw new IOException("Expected five tab-separated fields");
            }
            Course course = new Course(fields[0], fields[1], fields[2], Integer.parseInt(fields[3]), fields[4]);
            if (!codes.add(course.getCode().toUpperCase(Locale.ROOT))) {
                throw new IOException("Duplicate course code");
            }
            courses.add(course);
            line = reader.readLine();
        }
        return List.copyOf(courses);
    }
}
