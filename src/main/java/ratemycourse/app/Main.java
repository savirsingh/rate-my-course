package ratemycourse.app;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import ratemycourse.dataaccess.HttpCourseCatalogue;
import ratemycourse.dataaccess.TsvCourseCatalogue;
import ratemycourse.interfaceadapter.search.SearchPresenter;
import ratemycourse.interfaceadapter.search.SearchViewModel;
import ratemycourse.usecase.search.CourseCatalogue;
import ratemycourse.usecase.search.SearchCourses;
import ratemycourse.view.CourseBrowser;

/** Composition root: only this layer knows the concrete UI and data adapter. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String cataloguePath = System.getProperty("catalogue.path");
        CourseCatalogue catalogue;
        String label;
        if (cataloguePath != null) {
            catalogue = new TsvCourseCatalogue(() -> Files.newInputStream(Path.of(cataloguePath)));
            label = "Local TSV catalogue";
        }
        else if ("demo".equals(System.getProperty("catalogue.mode"))) {
            catalogue = new TsvCourseCatalogue(() -> Main.class.getResourceAsStream("/demo-courses.tsv"));
            label = "Demo catalogue — illustrative metadata, not a live UTSG feed";
        }
        else {
            URI api = URI.create(System.getProperty("catalogue.api", "http://127.0.0.1:8000/"));
            catalogue = new HttpCourseCatalogue(api);
            label = "Unofficial UofT API — Arts & Science 2026–2027; department/subject derived from code prefix";
        }
        SearchViewModel viewModel = new SearchViewModel();
        SearchCourses searchCourses = new SearchCourses(catalogue, new SearchPresenter(viewModel));
        String catalogueLabel = label;
        SwingUtilities.invokeLater(() -> new CourseBrowser(searchCourses, viewModel, catalogueLabel).setVisible(true));
    }
}
