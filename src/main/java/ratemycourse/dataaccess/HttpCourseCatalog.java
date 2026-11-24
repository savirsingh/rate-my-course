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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.CourseCatalog;
import ratemycourse.usecase.gateway.DataAccessException;

public final class HttpCourseCatalog implements CourseCatalog {
    private static final URI BASE = URI.create("https://unofficial-uoft-course-api-26-27.onrender.com");
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private final URI base;
    private final HttpClient client;

    public HttpCourseCatalog() {
        this(BASE);
    }

    HttpCourseCatalog(URI base) {
        this.base = base;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    }

    @Override
    public Optional<Course> findByCode(String code) throws DataAccessException {
        HttpRequest request = HttpRequest.newBuilder(base.resolve("/courses/" + code))
                .timeout(Duration.ofSeconds(90)).header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = sendWithRetry(request);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DataAccessException("The catalog request was interrupted.", ex);
        } catch (IOException ex) {
            throw new DataAccessException("The catalog could not be reached. Check your connection and try again.", ex);
        }
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
