package ratemycourse.interfaceadapter.search;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ratemycourse.usecase.search.SearchRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchControllerTest {
    @Test
    void controllerNormalizesTheFormAndPreservesCriteriaWhenPaging() {
        List<SearchRequest> calls = new ArrayList<>();
        SearchController controller = new SearchController(calls::add);
        controller.search(" cSc 207 ", " CSC ", 2);
        controller.page(2);
        assertEquals("csc207", calls.get(0).getQuery());
        assertEquals("csc", calls.get(1).getDepartment());
        assertEquals(2, calls.get(1).getYear());
        assertEquals(2, calls.get(1).getPage());
    }
}
