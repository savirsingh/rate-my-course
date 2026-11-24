package ratemycourse.dataaccess;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ratemycourse.entity.Course;
import ratemycourse.usecase.search.CatalogueException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCourseCatalogueTest {
    private HttpServer server;
    private ExecutorService executor;
    private URI base;
    private final AtomicInteger status = new AtomicInteger(200);
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile Function<String, String> response = query -> page(1, 1, List.of(record("CSC207H1")));
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile boolean delay;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/api/courses", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            requests.add(query);
            if (delay) {
                try {
                    release.await();
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = response.apply(query).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            finally {
                exchange.close();
            }
        });
        server.start();
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api");
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void loadsAllPagesBeforePublishingAndReusesImmutableSnapshot() throws CatalogueException {
        List<JsonObject> firstPage = new ArrayList<>();
        for (int number = 100; number < 200; number++) {
            firstPage.add(record("CSC" + number + "H1"));
        }
        response = query -> query.endsWith("page=1") ? page(101, 1, firstPage)
                : page(101, 2, List.of(record("CSC207H1")));
        HttpCourseCatalogue adapter = new HttpCourseCatalogue(base);
        List<Course> courses = adapter.loadCourses();
        assertEquals(101, courses.size());
        Course selected = courses.get(100);
        assertEquals("CSC207H1", selected.getCode());
        assertEquals("Software Design", selected.getName());
        assertEquals("CSC", selected.getDepartment());
        assertEquals(2, selected.getYear());
        assertEquals("https://artsci.calendar.utoronto.ca/course/csc207h1", selected.getSourceUrl());
        assertTrue(selected.getDescription().contains("Description not supplied"));
        assertSame(courses, adapter.loadCourses());
        assertThrows(UnsupportedOperationException.class, courses::clear);
        assertEquals(List.of("faculty=artsci&page_size=100&page=1", "faculty=artsci&page_size=100&page=2"), requests);
    }

    @Test
    void preservesNonemptyDescriptions() throws CatalogueException {
        JsonObject record = record("CSC207H1");
        record.addProperty("description", "Actual description from a configured snapshot");
        response = query -> page(1, 1, List.of(record));
        assertTrue(new HttpCourseCatalogue(base).loadCourses().get(0).getDescription()
                .startsWith("Actual description"));
    }

    @Test
    void acceptsAndCachesAnExplicitEmptyCatalogue() throws CatalogueException {
        response = query -> page(0, 1, List.of());
        HttpCourseCatalogue adapter = new HttpCourseCatalogue(base);
        assertTrue(adapter.loadCourses().isEmpty());
        assertTrue(adapter.loadCourses().isEmpty());
        assertEquals(1, requests.size());
    }

    @Test
    void failedLaterPageDoesNotCachePartialResultsAndRetryStartsFromPageOne() throws CatalogueException {
        List<JsonObject> firstPage = new ArrayList<>();
        for (int number = 100; number < 200; number++) {
            firstPage.add(record("CSC" + number + "H1"));
        }
        response = query -> query.endsWith("page=1") ? page(101, 1, firstPage) : "broken";
        HttpCourseCatalogue adapter = new HttpCourseCatalogue(base);
        assertThrows(CatalogueException.class, adapter::loadCourses);
        response = query -> page(1, 1, List.of(record("CSC207H1")));
        assertEquals(1, adapter.loadCourses().size());
        assertEquals(3, requests.size());
        assertTrue(requests.get(2).endsWith("page=1"));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 422, 500, 503})
    void treatsHttpErrorsAsFailuresAndCanRetry(int code) throws CatalogueException {
        HttpCourseCatalogue adapter = new HttpCourseCatalogue(base);
        status.set(code);
        assertThrows(CatalogueException.class, adapter::loadCourses);
        status.set(200);
        assertEquals(1, adapter.loadCourses().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>Error</html>", "null", "[]", "{}",
        "{\"total\":1,\"page\":1,\"page_size\":100,\"courses\":[]}",
        "{\"total\":-1,\"page\":1,\"page_size\":100,\"courses\":[]}",
        "{\"total\":0,\"page\":2,\"page_size\":100,\"courses\":[]}",
        "{\"total\":0,\"page\":1,\"page_size\":20,\"courses\":[]}",
        "{\"total\":\"0\",\"page\":1,\"page_size\":100,\"courses\":[]}"})
    void rejectsMalformedOrInconsistentResponses(String body) {
        response = query -> body;
        assertThrows(CatalogueException.class, () -> new HttpCourseCatalogue(base).loadCourses());
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "title", "description", "source_url", "faculties"})
    void missingConsumedFieldsAreFailures(String field) {
        JsonObject record = record("CSC207H1");
        record.remove(field);
        response = query -> page(1, 1, List.of(record));
        assertThrows(CatalogueException.class, () -> new HttpCourseCatalogue(base).loadCourses());
    }

    @Test
    void rejectsOutOfScopeAndDuplicateRecords() {
        JsonObject record = record("CSC207H1");
        record.add("faculties", new JsonArray());
        response = query -> page(1, 1, List.of(record));
        assertThrows(CatalogueException.class, () -> new HttpCourseCatalogue(base).loadCourses());
        response = query -> page(1, 1, List.of(record("BAD-CODE")));
        assertThrows(CatalogueException.class, () -> new HttpCourseCatalogue(base).loadCourses());
        response = query -> page(2, 1, List.of(record("CSC207H1"), record("CSC207H1")));
        assertThrows(CatalogueException.class, () -> new HttpCourseCatalogue(base).loadCourses());
    }

    @Test
    void boundsRequestDuration() {
        delay = true;
        HttpCourseCatalogue adapter = new HttpCourseCatalogue(HttpClient.newHttpClient(), base,
                Duration.ofMillis(300));
        CatalogueException exception = assertThrows(CatalogueException.class, adapter::loadCourses);
        assertTrue(exception.getCause() instanceof HttpTimeoutException);
    }

    @Test
    void preservesInterruptedFlag() {
        HttpCourseCatalogue adapter = new HttpCourseCatalogue(base);
        try {
            Thread.currentThread().interrupt();
            assertThrows(CatalogueException.class, adapter::loadCourses);
            assertTrue(Thread.currentThread().isInterrupted());
        }
        finally {
            Thread.interrupted();
        }
        assertFalse(Thread.currentThread().isInterrupted());
    }

    private static JsonObject record(String code) {
        JsonObject record = new JsonObject();
        record.addProperty("code", code);
        record.addProperty("title", "Software Design");
        record.addProperty("description", "");
        record.addProperty("source_url", "https://artsci.calendar.utoronto.ca/course/" + code.toLowerCase());
        JsonArray faculties = new JsonArray();
        faculties.add("artsci");
        record.add("faculties", faculties);
        return record;
    }

    private static String page(int total, int number, List<JsonObject> records) {
        JsonObject page = new JsonObject();
        page.addProperty("total", total);
        page.addProperty("page", number);
        page.addProperty("page_size", 100);
        JsonArray courses = new JsonArray();
        records.forEach(courses::add);
        page.add("courses", courses);
        return page.toString();
    }
}
