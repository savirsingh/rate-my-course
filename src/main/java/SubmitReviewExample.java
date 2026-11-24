import javax.swing.*;

public class SubmitReviewExample {

    /**
     * Example code that shows roughly the appearance of the UI for submitting a course review
     *
     * @param args arguments
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JPanel enjoymentPanel = new JPanel();
            enjoymentPanel.setLayout(new BoxLayout(enjoymentPanel, BoxLayout.Y_AXIS));
            enjoymentPanel.add(new JLabel("Enjoyment (1-5)"));
            enjoymentPanel.add(new JTextField(10));

            JPanel difficultyPanel = new JPanel();
            difficultyPanel.setLayout(new BoxLayout(difficultyPanel, BoxLayout.Y_AXIS));
            difficultyPanel.add(new JLabel("Difficulty (1-5)"));
            difficultyPanel.add(new JTextField(10));

            JPanel workloadPanel = new JPanel();
            workloadPanel.setLayout(new BoxLayout(workloadPanel, BoxLayout.Y_AXIS));
            workloadPanel.add(new JLabel("Workload (1-5)"));
            workloadPanel.add(new JTextField(10));

            JPanel subPanel = new JPanel();
            subPanel.add(enjoymentPanel);
            subPanel.add(difficultyPanel);
            subPanel.add(workloadPanel);

            JPanel buttonPanel = new JPanel();
            buttonPanel.add(new JButton("Submit review"));
            buttonPanel.add(new JButton("Cancel"));

            JPanel mainPanel = new JPanel();
            mainPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
            mainPanel.add(new JLabel("Term taken"));
            mainPanel.add(new JTextField(10));
            mainPanel.add(subPanel);
            mainPanel.add(new JLabel("Comment"));
            mainPanel.add(new JTextArea(5, 10));
            mainPanel.add(buttonPanel);

            JFrame frame = new JFrame("Submit review UI example");
            frame.setContentPane(mainPanel);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.pack();
            frame.setVisible(true);
        });
    }
}
