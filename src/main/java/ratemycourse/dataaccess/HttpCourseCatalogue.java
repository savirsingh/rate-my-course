package ratemycourse.dataaccess;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ratemycourse.entity.Course;
import ratemycourse.usecase.search.CatalogueException;
import ratemycourse.usecase.search.CourseCatalogue;

/** HTTP adapter for the unofficial API. Publishes a snapshot only after all pages validate. */
public final class HttpCourseCatalogue implements CourseCatalogue {
    private static final int PAGE_SIZE = 100;
    private static final int MAX_COURSES = 20000;
    private static final Duration LOAD_TIMEOUT = Duration.ofSeconds(60);
    private static final Pattern CODE = Pattern.compile("([A-Z]{2,5})([1-4])[0-9]{2}[A-Z][0-9]?");
    private final HttpClient client;
    private final URI base;
    private final Duration requestTimeout;
    private List<Course> snapshot;

    public HttpCourseCatalogue(URI base) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1).build(), base, Duration.ofSeconds(5));
    }

    HttpCourseCatalogue(HttpClient client, URI base, Duration requestTimeout) {
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalArgumentException("API base must be an absolute HTTP(S) URL without query or fragment");
        }
        this.client = client;
        this.base = URI.create(base.toString().endsWith("/") ? base.toString() : base + "/");
        this.requestTimeout = requestTimeout;
    }

    @Override
    public synchronized List<Course> loadCourses() throws CatalogueException {
        if (snapshot != null) {
            return snapshot;
        }
        try {
            snapshot = fetchSnapshot();
            return snapshot;
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CatalogueException("Catalogue request interrupted", exception);
        }
        catch (IOException | RuntimeException exception) {
            throw new CatalogueException("API catalogue could not be loaded", exception);
        }
    }

    private List<Course> fetchSnapshot() throws IOException, InterruptedException {
        long started = System.nanoTime();
        List<Course> courses = new ArrayList<>();
        Set<String> codes = new HashSet<>();
        int expectedTotal = -1;
        int page = 1;
        do {
            Duration remaining = LOAD_TIMEOUT.minusNanos(System.nanoTime() - started);
            if (remaining.isNegative() || remaining.isZero()) {
                throw new IOException("Catalogue load deadline exceeded");
            }
            Duration timeout = remaining.compareTo(requestTimeout) < 0 ? remaining : requestTimeout;
            JsonObject document = fetchPage(page, timeout);
            int total = integer(document, "total");
            if (expectedTotal == -1) {
                expectedTotal = total;
            }
            if (total != expectedTotal || total < 0 || total > MAX_COURSES
                    || integer(document, "page") != page || integer(document, "page_size") != PAGE_SIZE) {
                throw new IOException("Inconsistent API pagination");
            }
            JsonElement items = document.get("courses");
            if (items == null || !items.isJsonArray()) {
                throw new IOException("Missing courses array");
            }
            JsonArray records = items.getAsJsonArray();
            if (records.size() != Math.min(PAGE_SIZE, total - courses.size())) {
                throw new IOException("Incomplete API page");
            }
            for (JsonElement record : records) {
                Course course = mapCourse(record.getAsJsonObject());
                if (!codes.add(course.getCode())) {
                    throw new IOException("Duplicate course across API pages");
                }
                courses.add(course);
            }
            page++;
        } while (courses.size() < expectedTotal);
        return List.copyOf(courses);
    }

    private JsonObject fetchPage(int page, Duration timeout) throws IOException, InterruptedException {
        URI uri = base.resolve("courses?faculty=artsci&page_size=" + PAGE_SIZE + "&page=" + page);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout)
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Catalogue API returned HTTP " + response.statusCode());
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private Course mapCourse(JsonObject record) throws IOException {
        String code = text(record, "code");
        Matcher match = CODE.matcher(code);
        if (!match.matches()) {
            throw new IOException("Unsupported Arts & Science course code");
        }
        JsonArray faculties = record.getAsJsonArray("faculties");
        if (faculties == null || !faculties.contains(new JsonPrimitive("artsci"))) {
            throw new IOException("Course is outside the requested Arts & Science faculty");
        }
        String description = text(record, "description");
        if (description.isBlank()) {
            description = "Description not supplied by the public API.";
        }
        return new Course(code, text(record, "title"), match.group(1), Integer.parseInt(match.group(2)),
                description, text(record, "source_url"));
    }

    private static String text(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Missing or invalid string: " + field);
        }
        return value.getAsString();
    }

    private static int integer(JsonObject object, String field) throws IOException {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IOException("Missing or invalid integer: " + field);
        }
        return value.getAsBigDecimal().intValueExact();
    }
}
