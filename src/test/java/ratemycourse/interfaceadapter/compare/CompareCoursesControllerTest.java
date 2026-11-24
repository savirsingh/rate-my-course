package ratemycourse.interfaceadapter.compare;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import ratemycourse.usecase.compare.ComparisonInput;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompareCoursesControllerTest {
    @Test
    void sendsOneInputObjectWithBothSelectionsInOrderThroughTheBoundary() {
        List<ComparisonInput> requests = new ArrayList<>();
        CompareCoursesController controller = new CompareCoursesController(requests::add);

        controller.compare(" mat237y1 ", "CSC207H1");

        assertEquals(1, requests.size());
        assertEquals(" mat237y1 ", requests.get(0).firstCode);
        assertEquals("CSC207H1", requests.get(0).secondCode);
    }
}
