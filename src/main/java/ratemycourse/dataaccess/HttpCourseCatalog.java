package ratemycourse.dataaccess;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.CourseCatalog;
import ratemycourse.usecase.gateway.DataAccessException;
import ratemycourse.usecase.gateway.CourseSearchCatalog;
import ratemycourse.usecase.gateway.CourseSearchPage;

public final class HttpCourseCatalog implements CourseCatalog, CourseSearchCatalog {
    private static final URI BASE = URI.create("https://unofficial-uoft-course-api-26-27.onrender.com");
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final URI base;
    private final HttpClient client;
    private final Duration requestTimeout;

    public HttpCourseCatalog() {
        this(BASE);
    }

    public HttpCourseCatalog(URI base) {
        this(base, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build(), Duration.ofSeconds(90));
    }

    HttpCourseCatalog(URI base, HttpClient client, Duration requestTimeout) {
        this.base = base;
        this.client = client;
        this.requestTimeout = requestTimeout;
    }

    @Override
    public Optional<Course> findByCode(String code) throws DataAccessException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/courses/" + code))
                .timeout(requestTimeout).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = send(request);
        switch (response.statusCode()) {
            case 200:
                return Optional.of(parseCourse(code, response.body()));
            case 404:
                return Optional.empty();
            case 422:
                throw new DataAccessException("The catalog rejected this course code.");
            default:
                throw new DataAccessException("The catalog returned HTTP " + response.statusCode() + ". Try again later.");
        }
    }

    private HttpResponse<String> send(HttpRequest request) throws DataAccessException {
        try {
            return sendWithRetry(request);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("The catalog request was interrupted.", ex);
        } catch (IOException ex) {
            throw new DataAccessException("The catalog could not be reached. Check your connection and try again.", ex);
        }
    }

    @Override
    public CourseSearchPage search(String query, int page, int pageSize) throws DataAccessException {
        if (query == null || query.length() > 200 || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new DataAccessException("Invalid search query or pagination.");
        }
        String path = "/courses?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&page=" + page + "&page_size=" + pageSize;
        HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).timeout(requestTimeout)
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() != 200) {
            throw new DataAccessException("The catalog returned HTTP " + response.statusCode() + ". Try again later.");
        }
        try {
            JsonObject document = JSON.fromJson(response.body(), JsonObject.class);
            int total = integer(document, "total");
            if (total < 0 || integer(document, "page") != page || integer(document, "page_size") != pageSize) {
                throw new IllegalArgumentException("Inconsistent pagination");
            }
            List<Course> courses = new ArrayList<>();
            Set<String> codes = new LinkedHashSet<>();
            for (JsonElement item : document.getAsJsonArray("courses")) {
                JsonObject record = item.getAsJsonObject();
                String code = text(record.get("code")).toUpperCase(Locale.ROOT);
                if (!code.matches("[A-Z]{2,5}[0-9]{3}[A-Z][0-9]?") || !codes.add(code)) {
                    throw new IllegalArgumentException("Invalid or duplicate course code");
                }
                courses.add(parseRecord(code, record));
            }
            long offset = ((long) page - 1) * pageSize;
            long expectedSize = Math.min(pageSize, Math.max(0, total - offset));
            if (courses.size() != expectedSize) {
                throw new IllegalArgumentException("Incomplete search page");
            }
            return new CourseSearchPage(courses, total, page, pageSize);
        } catch (RuntimeException ex) {
            throw new DataAccessException("The catalog returned an unreadable search page.", ex);
        }
    }

    private static int integer(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing pagination integer");
        }
        return value.getAsBigDecimal().intValueExact();
    }

    private HttpResponse<String> sendWithRetry(HttpRequest request) throws IOException, InterruptedException {
        for (int attempt = 1; ; attempt++) {
            try {
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                boolean transientFailure = status == 502 || status == 503 || status == 504;
                if (!transientFailure || attempt == 2) {
                    return response;
                }
            } catch (IOException ex) {
                if (attempt == 2) {
                    throw ex;
                }
            }
            Thread.sleep(1000);
        }
    }

    private static Course parseCourse(String requested, String body) throws DataAccessException {
        JsonObject record;
        try {
            record = JSON.fromJson(body, JsonObject.class);
        } catch (JsonParseException ex) {
            throw new DataAccessException("The catalog returned an unreadable course record.", ex);
        }
        return parseRecord(requested, record);
    }

    private static Course parseRecord(String requested, JsonObject record) throws DataAccessException {
        if (record == null || !requested.equalsIgnoreCase(text(record.get("code")))) {
            throw new DataAccessException("The catalog response did not identify the requested course.");
        }

        JsonElement creditsField = record.get("credits");
        BigDecimal credits = null;
        if (creditsField != null && creditsField.isJsonPrimitive() && creditsField.getAsJsonPrimitive().isNumber()) {
            try {
                credits = creditsField.getAsBigDecimal();
            } catch (NumberFormatException ex) {
                throw new DataAccessException("The catalog returned an unreadable credit value.", ex);
            }
        }
        Set<String> links = new LinkedHashSet<>();
        addLink(links, record.get("source_url"));
        JsonElement sources = record.get("source_urls");
        if (sources != null && sources.isJsonArray()) {
            sources.getAsJsonArray().forEach(source -> addLink(links, source));
        }
        return new Course(requested, text(record.get("title")), text(record.get("faculty")), credits,
                List.copyOf(links));
    }

    private static String text(JsonElement field) {
        if (field != null && field.isJsonPrimitive() && field.getAsJsonPrimitive().isString()) {
            return field.getAsString().strip();
        }
        return "";
    }

    private static void addLink(Set<String> links, JsonElement field) {
        String link = text(field);
        try {
            URI uri = URI.create(link);
            if (uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                links.add(link);
            }
        } catch (IllegalArgumentException ignored) {
            // Other source URLs in the same record can still be displayed.
        }
    }
}
