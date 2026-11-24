package ratemycourse.entity;

/** The three ratings UC3 reads from one student review. */
public final class Review {
    public final int enjoyment;
    public final int difficulty;
    public final int workload;

    public Review(int enjoyment, int difficulty, int workload) {
        this.enjoyment = rating(enjoyment);
        this.difficulty = rating(difficulty);
        this.workload = rating(workload);
    }

    private static int rating(int value) {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("Ratings must be between 1 and 5.");
        }
        return value;
    }
}
