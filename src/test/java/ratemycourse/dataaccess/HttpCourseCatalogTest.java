package ratemycourse.dataaccess;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ratemycourse.entity.Course;
import ratemycourse.usecase.gateway.DataAccessException;

import static org.junit.jupiter.api.Assertions.*;

class HttpCourseCatalogTest {
    private static final String CODE = "CSC207H1";
    private static final String MINIMAL = "{\"code\":\"CSC207H1\"}";
    private static final String LINK = "https://one.example/course?page=2";
    private static final String SECOND_LINK = "https://two.example/course";
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private HttpCourseCatalog catalog;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        catalog = new HttpCourseCatalog(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void serve(IntUnaryOperator statusForAttempt, String json) {
        server.createContext("/courses/" + CODE, exchange -> {
            try {
                int status = statusForAttempt.applyAsInt(requests.incrementAndGet());
                if (!exchange.getRequestMethod().equals("GET")
                        || !exchange.getRequestURI().getPath().equals("/courses/" + CODE)) {
                    status = 405;
                }
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    @Test
    void readsExactCourseAndKeepsDistinctCalendarLinksInSourceOrder() throws DataAccessException {
        String json = "{\"code\":\"CSC207H1\",\"title\":\"Études\",\"faculty\":\"Test faculty\","
                + "\"credits\":0.5,\"source_url\":\"" + LINK + "\",\"source_urls\":[\"" + LINK
                + "\",null,\"bad link\",\"file:///unrelated\",\"" + SECOND_LINK + "\"]}";
        serve(i -> 200, json);
        Course course = catalog.findByCode(CODE).orElseThrow();
        assertEquals(CODE, course.code);
        assertEquals("Études", course.title);
        assertEquals("Test faculty", course.faculty);
        assertEquals("0.5", course.credits.orElseThrow().toPlainString());
        assertEquals(List.of(LINK, SECOND_LINK), course.calendarLinks);
        assertEquals(1, requests.get());
    }

    @Test
    void missingOptionalFieldsRemainMissingAndSecondarySourceIsUsable() throws DataAccessException {
        serve(i -> 200, "{\"code\":\"CSC207H1\",\"title\":null,\"credits\":null,\"source_urls\":[\"" + LINK + "\"]}");
        Course course = catalog.findByCode(CODE).orElseThrow();
        assertTrue(course.title.isEmpty());
        assertTrue(course.faculty.isEmpty());
        assertTrue(course.credits.isEmpty());
        assertEquals(List.of(LINK), course.calendarLinks);
    }

    @Test
    void explicitZeroCreditsRemainDistinctFromMissingCredits() throws DataAccessException {
        serve(i -> 200, "{\"code\":\"CSC207H1\",\"credits\":0}");
        assertEquals(0, catalog.findByCode(CODE).orElseThrow().credits.orElseThrow().signum());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "[]", "{}", "null", "{\"code\":\"MAT237Y1\"}"})
    void rejectsUnreadableOrMisidentifiedRecords(String body) {
        serve(i -> 200, body);
        assertThrows(DataAccessException.class, () -> catalog.findByCode(CODE));
        assertEquals(1, requests.get());
    }

    @Test
    void unknownCourseIsAnEmptyLookupWithoutRetry() throws DataAccessException {
        serve(i -> 404, "{}");
        assertTrue(catalog.findByCode(CODE).isEmpty());
        assertEquals(1, requests.get());
    }

    @Test
    void rejectedCourseCodeIsReportedWithoutRetry() {
        serve(i -> 422, "{}");
        DataAccessException error = assertThrows(DataAccessException.class, () -> catalog.findByCode(CODE));
        assertTrue(error.getMessage().contains("rejected"));
        assertEquals(1, requests.get());
    }

    @Test
    void recoversWhenCatalogBecomesReady() throws DataAccessException {
        serve(attempt -> attempt == 1 ? 503 : 200, MINIMAL);
        assertEquals(CODE, catalog.findByCode(CODE).orElseThrow().code);
        assertEquals(2, requests.get());
    }

    @Test
    void persistentServiceFailureStopsAfterOneRetry() {
        serve(i -> 503, "{}");
        DataAccessException error = assertThrows(DataAccessException.class, () -> catalog.findByCode(CODE));
        assertTrue(error.getMessage().contains("503"));
        assertEquals(2, requests.get());
    }
}
