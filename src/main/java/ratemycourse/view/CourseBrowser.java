package ratemycourse.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import ratemycourse.usecase.search.CourseData;
import ratemycourse.usecase.search.SearchInputBoundary;
import ratemycourse.usecase.search.SearchRequest;
import ratemycourse.usecase.search.SearchResponse;

/** Thin Swing adapter: collect input, call the boundary off the UI thread, render output. */
public final class CourseBrowser extends JFrame {
    private final SearchInputBoundary searchCourses;
    private final JTextField query = new JTextField(20);
    private final JComboBox<String> department = new JComboBox<>(new String[]{"All departments"});
    private final JComboBox<String> year = new JComboBox<>(new String[]{"All years", "1", "2", "3", "4"});
    private final JButton search = new JButton("Search");
    private final JButton retry = new JButton("Retry");
    private final JLabel status = new JLabel("Loading catalogue...");
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> results = new JList<>(listModel);
    private final JTextArea details = new JTextArea();
    private List<CourseData> displayedCourses = List.of();

    public CourseBrowser(SearchInputBoundary searchCourses) {
        this(searchCourses, "Demo catalogue — illustrative metadata, not a live UTSG feed");
    }

    public CourseBrowser(SearchInputBoundary searchCourses, String catalogueLabel) {
        super("Rate My Course — UC1 search preview");
        this.searchCourses = searchCourses;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1050, 550);
        setLocationRelativeTo(null);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addLabelled(filters, "Course code or name", query);
        addLabelled(filters, "Department / subject", department);
        addLabelled(filters, "Year", year);
        filters.add(search);
        filters.add(retry);
        retry.setVisible(false);
        status.getAccessibleContext().setAccessibleName("Search status");
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        details.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        details.getAccessibleContext().setAccessibleName("Selected course metadata");
        results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        results.getAccessibleContext().setAccessibleName("Matching courses");
        results.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showSelection();
            }
        });
        JSplitPane content = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(results), new JScrollPane(details));
        content.setResizeWeight(0.5);
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(new JLabel(catalogueLabel), BorderLayout.NORTH);
        heading.add(filters, BorderLayout.CENTER);
        heading.add(status, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        search.addActionListener(event -> submit());
        query.addActionListener(event -> submit());
        retry.addActionListener(event -> submit());
        getRootPane().setDefaultButton(search);
        submit();
    }

    private static void addLabelled(JPanel panel, String text, javax.swing.JComponent component) {
        JLabel label = new JLabel(text);
        label.setLabelFor(component);
        panel.add(label);
        panel.add(component);
    }

    private void submit() {
        String selectedDepartment = department.getSelectedIndex() <= 0 ? "" : (String) department.getSelectedItem();
        load(new SearchRequest(query.getText(), selectedDepartment, year.getSelectedIndex()));
    }

    private void load(SearchRequest request) {
        setBusy(true);
        status.setText("Loading catalogue...");
        retry.setVisible(false);
        displayedCourses = List.of();
        listModel.clear();
        details.setText("");
        new SwingWorker<SearchResponse, Void>() {
            @Override
            protected SearchResponse doInBackground() {
                return searchCourses.execute(request);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) {
                    return;
                }
                setBusy(false);
                try {
                    render(get());
                }
                catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    showFailure("Search was interrupted. Please retry.");
                }
                catch (ExecutionException exception) {
                    showFailure("Search could not finish. Please retry.");
                }
            }
        }.execute();
    }

    private void render(SearchResponse response) {
        if (!response.isSuccess()) {
            showFailure(response.getError());
            return;
        }
        Object selectedDepartment = department.getSelectedItem();
        department.removeAllItems();
        department.addItem("All departments");
        response.getDepartments().forEach(department::addItem);
        if (response.getDepartments().contains(selectedDepartment)) {
            department.setSelectedItem(selectedDepartment);
        }
        displayedCourses = response.getCourses();
        for (CourseData course : displayedCourses) {
            listModel.addElement(course.getCode() + " — " + course.getName());
        }
        int count = displayedCourses.size();
        status.setText(count == 0 ? "No matching courses. Change your search or filters."
                : count + (count == 1 ? " matching course" : " matching courses"));
        details.setText("Select a result to inspect its course metadata.");
    }

    private void showFailure(String message) {
        status.setText(message);
        retry.setVisible(true);
    }

    private void setBusy(boolean busy) {
        search.setEnabled(!busy);
        query.setEnabled(!busy);
        department.setEnabled(!busy);
        year.setEnabled(!busy);
        retry.setEnabled(!busy);
    }

    private void showSelection() {
        int index = results.getSelectedIndex();
        if (index >= 0 && index < displayedCourses.size()) {
            CourseData course = displayedCourses.get(index);
            details.setText(course.getCode() + " — " + course.getName() + "\n\n"
                    + course.getDepartment() + " | Year " + course.getYear() + "\n\n"
                    + course.getDescription()
                    + (course.getSourceUrl().isEmpty() ? "" : "\nOfficial listing: " + course.getSourceUrl()) + "\n\n"
                    + "Reviews are not available in this preview.\n"
                    + "Reviewing, saving, and comparing are not available yet.");
            details.setCaretPosition(0);
        }
    }
}
