package ratemycourse.app;

import java.io.PrintWriter;
import java.util.Scanner;
import ratemycourse.console.ComparisonConsole;
import ratemycourse.dataaccess.DemoReviewRepository;
import ratemycourse.dataaccess.HttpCourseCatalog;
import ratemycourse.interfaceadapter.compare.CompareCoursesController;
import ratemycourse.interfaceadapter.compare.CompareCoursesPresenter;
import ratemycourse.interfaceadapter.compare.CompareCoursesViewModel;
import ratemycourse.usecase.compare.CompareCoursesInteractor;
import ratemycourse.usecase.gateway.CourseCatalog;
import ratemycourse.usecase.gateway.ReviewRepository;

public final class CompareCoursesMain {
    private CompareCoursesMain() {
    }

    public static void main(String[] args) {
        createConsole(new HttpCourseCatalog(), new DemoReviewRepository(), "DEMO (fictional in-memory reviews)",
                new Scanner(System.in), new PrintWriter(System.out, true)).run();
    }

    public static ComparisonConsole createConsole(CourseCatalog catalog, ReviewRepository reviews, String reviewSource,
            Scanner input, PrintWriter out) {
        CompareCoursesViewModel viewModel = new CompareCoursesViewModel(reviewSource);
        CompareCoursesPresenter presenter = new CompareCoursesPresenter(viewModel);
        CompareCoursesInteractor interactor = new CompareCoursesInteractor(catalog, reviews, presenter);
        CompareCoursesController controller = new CompareCoursesController(interactor);
        return new ComparisonConsole(controller, viewModel, input, out);
    }
}
