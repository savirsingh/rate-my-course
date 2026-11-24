package ratemycourse.dataaccess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.CourseSearchCatalog;
import ratemycourse.usecase.gateway.CourseSearchPage;
import ratemycourse.usecase.gateway.DataAccessException;

/** Explicit offline fixture/file adapter. Normal application operation uses HttpCourseCatalog. */
public final class TsvCourseCatalogue implements CourseSearchCatalog {
    private static final String HEADER = "code\tname\tfaculty\tcredits\tsource_url";
    private final Source source;

    @FunctionalInterface
    public interface Source {
        InputStream open() throws IOException;
    }

    public TsvCourseCatalogue(Source source) { this.source = Objects.requireNonNull(source); }

    @Override
    public CourseSearchPage search(String query, int page, int pageSize) throws DataAccessException {
        String term = normalize(query);
        List<Course> matches = loadCourses().stream().filter(course -> normalize(course.code).contains(term)
                || normalize(course.title).contains(term)).sorted(Comparator.comparing(course -> course.code))
                .collect(Collectors.toList());
        int from = (int) Math.min(((long) page - 1) * pageSize, matches.size());
        return new CourseSearchPage(matches.subList(from, Math.min(from + pageSize, matches.size())),
                matches.size(), page, pageSize);
    }

    public List<Course> loadCourses() throws DataAccessException {
        try (InputStream stream = source.open()) {
            if (stream == null) {
                throw new IOException("Catalog resource is missing");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                if (!HEADER.equals(reader.readLine())) {
                    throw new IOException("Invalid catalog header");
                }
                List<Course> courses = new ArrayList<>();
                Set<String> codes = new HashSet<>();
                String line = reader.readLine();
                while (line != null) {
                    String[] fields = line.split("\t", -1);
                    if (fields.length != 5 || !fields[0].matches("[A-Za-z]{2,5}[0-9]{3}[A-Za-z][0-9]?")
                            || fields[1].isBlank() || !codes.add(fields[0].toUpperCase(Locale.ROOT))) {
                        throw new IOException("Invalid or duplicate catalog record");
                    }
                    BigDecimal credits = fields[3].isEmpty() ? null : new BigDecimal(fields[3]);
                    courses.add(new Course(fields[0].toUpperCase(Locale.ROOT), fields[1], fields[2], credits,
                            fields[4].isEmpty() ? List.of() : List.of(fields[4])));
                    line = reader.readLine();
                }
                return List.copyOf(courses);
            }
        }
        catch (IOException | IllegalArgumentException exception) {
            throw new DataAccessException("The local catalog could not be read.", exception);
        }
    }

    private static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
