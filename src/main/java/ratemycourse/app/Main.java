package ratemycourse.app;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import ratemycourse.dataaccess.HttpCourseCatalog;
import ratemycourse.dataaccess.TsvCourseCatalogue;
import ratemycourse.interfaceadapter.search.SearchController;
import ratemycourse.interfaceadapter.search.SearchPresenter;
import ratemycourse.interfaceadapter.search.SearchViewModel;
import ratemycourse.usecase.gateway.CourseSearchCatalog;
import ratemycourse.usecase.search.SearchCourses;
import ratemycourse.view.CourseBrowser;

/** UC1 composition root; UC3's CompareCoursesMain remains independently runnable. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        CourseSearchCatalog catalog;
        String label;
        String path = System.getProperty("catalogue.path");
        if (path != null) {
            catalog = new TsvCourseCatalogue(() -> Files.newInputStream(Path.of(path)));
            label = "Explicit local TSV catalog";
        }
        else if ("demo".equals(System.getProperty("catalogue.mode"))) {
            catalog = new TsvCourseCatalogue(() -> Main.class.getResourceAsStream("/demo-courses.tsv"));
            label = "Explicit offline demo — eight illustrative records";
        }
        else {
            String base = System.getProperty("catalogue.api");
            catalog = base == null ? new HttpCourseCatalog() : new HttpCourseCatalog(URI.create(base));
            label = "Deployed UofT API — all eight faculties; subject/level derived from course codes";
        }
        SearchViewModel viewModel = new SearchViewModel();
        SearchCourses interactor = new SearchCourses(catalog, new SearchPresenter(viewModel));
        SearchController controller = new SearchController(interactor);
        String sourceLabel = label;
        SwingUtilities.invokeLater(() -> new CourseBrowser(controller, viewModel, sourceLabel).setVisible(true));
    }
}
