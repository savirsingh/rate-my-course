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
import ratemycourse.usecase.gateway.DataAccessException;
import ratemycourse.usecase.gateway.CourseSearchPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        server.createContext("/courses", exchange -> {
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
        base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void loadsOnlyTheRequestedPageAndKeepsSharedMetadata() throws DataAccessException {
        List<JsonObject> firstPage = new ArrayList<>();
        for (int number = 100; number < 200; number++) {
            firstPage.add(record("CSC" + number + "H1"));
        }
        response = query -> query.contains("page=1&") ? page(101, 1, firstPage)
                : page(101, 2, List.of(record("CSC207H1")));
        HttpCourseCatalog adapter = new HttpCourseCatalog(base);
        CourseSearchPage first = adapter.search("", 1, 100);
        assertEquals(101, first.total);
        assertEquals(100, first.courses.size());
        assertEquals(1, requests.size());
        Course selected = adapter.search("", 2, 100).courses.get(0);
        assertEquals("CSC207H1", selected.code);
        assertEquals("Software Design", selected.title);
        assertTrue(selected.credits.isEmpty());
        assertEquals(List.of("https://artsci.calendar.utoronto.ca/course/csc207h1"), selected.calendarLinks);
        assertThrows(UnsupportedOperationException.class, first.courses::clear);
        assertEquals(List.of("q=&page=1&page_size=100", "q=&page=2&page_size=100"), requests);
    }

    @Test
    void missingDescriptionsDoNotInvalidateSharedMetadata() throws DataAccessException {
        JsonObject record = record("CSC207H1");
        record.remove("description");
        record.addProperty("credits", 0.5);
        response = query -> page(1, 1, List.of(record));
        Course course = new HttpCourseCatalog(base).search("", 1, 100).courses.get(0);
        assertEquals("0.5", course.credits.orElseThrow().toPlainString());
    }

    @Test
    void acceptsAnExplicitEmptySearchPage() throws DataAccessException {
        response = query -> page(0, 1, List.of());
        HttpCourseCatalog adapter = new HttpCourseCatalog(base);
        assertTrue(adapter.search("", 1, 100).courses.isEmpty());
        assertTrue(adapter.search("", 1, 100).courses.isEmpty());
        assertEquals(2, requests.size());
    }

    @Test
    void failedPageCanBeRetriedWithoutPublishingPartialData() throws DataAccessException {
        List<JsonObject> firstPage = new ArrayList<>();
        for (int number = 100; number < 200; number++) {
            firstPage.add(record("CSC" + number + "H1"));
        }
        response = query -> query.contains("page=1&") ? page(101, 1, firstPage) : "broken";
        HttpCourseCatalog adapter = new HttpCourseCatalog(base);
        assertEquals(100, adapter.search("", 1, 100).courses.size());
        assertThrows(DataAccessException.class, () -> adapter.search("", 2, 100));
        response = query -> page(1, 1, List.of(record("CSC207H1")));
        assertEquals(1, adapter.search("", 1, 100).courses.size());
        assertEquals(3, requests.size());
        assertTrue(requests.get(2).contains("page=1&"));
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 422, 500, 503})
    void treatsHttpErrorsAsFailuresAndCanRetry(int code) throws DataAccessException {
        HttpCourseCatalog adapter = new HttpCourseCatalog(base);
        status.set(code);
        assertThrows(DataAccessException.class, () -> adapter.search("", 1, 100));
        status.set(200);
        assertEquals(1, adapter.search("", 1, 100).courses.size());
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
        assertThrows(DataAccessException.class, () -> new HttpCourseCatalog(base).search("", 1, 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"code", "title", "description", "source_url", "faculties"})
    void codeIsRequiredAndMissingOptionalFieldsRemainValid(String field) throws DataAccessException {
        JsonObject record = record("CSC207H1");
        record.remove(field);
        response = query -> page(1, 1, List.of(record));
        if (field.equals("code")) {
            assertThrows(DataAccessException.class, () -> new HttpCourseCatalog(base).search("", 1, 100));
        }
        else {
            assertEquals(1, new HttpCourseCatalog(base).search("", 1, 100).courses.size());
        }
    }

    @Test
    void supportsOtherFacultiesButRejectsInvalidAndDuplicateCodes() throws DataAccessException {
        JsonObject record = record("CSC207H1");
        record.add("faculties", new JsonArray());
        response = query -> page(1, 1, List.of(record));
        assertEquals(1, new HttpCourseCatalog(base).search("", 1, 100).courses.size());
        response = query -> page(1, 1, List.of(record("BAD-CODE")));
        assertThrows(DataAccessException.class, () -> new HttpCourseCatalog(base).search("", 1, 100));
        response = query -> page(2, 1, List.of(record("CSC207H1"), record("CSC207H1")));
        assertThrows(DataAccessException.class, () -> new HttpCourseCatalog(base).search("", 1, 100));
    }

    @Test
    void boundsRequestDuration() {
        delay = true;
        HttpCourseCatalog adapter = new HttpCourseCatalog(base, HttpClient.newHttpClient(),
                Duration.ofMillis(300));
        DataAccessException exception = assertThrows(DataAccessException.class, () -> adapter.search("", 1, 100));
        assertTrue(exception.getCause() instanceof HttpTimeoutException);
    }

    @Test
    void preservesInterruptedFlag() {
        HttpCourseCatalog adapter = new HttpCourseCatalog(base);
        try {
            Thread.currentThread().interrupt();
            assertThrows(DataAccessException.class, () -> adapter.search("", 1, 100));
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
