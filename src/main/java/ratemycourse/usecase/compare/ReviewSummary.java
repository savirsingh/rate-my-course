package ratemycourse.usecase.compare;

import java.util.List;
import java.util.OptionalDouble;
import ratemycourse.entity.Review;

public final class ReviewSummary {
    public final int count;
    public final OptionalDouble enjoyment;
    public final OptionalDouble difficulty;
    public final OptionalDouble workload;

    private ReviewSummary(int count, long enjoyment, long difficulty, long workload) {
        this.count = count;
        this.enjoyment = average(enjoyment, count);
        this.difficulty = average(difficulty, count);
        this.workload = average(workload, count);
    }

    public static ReviewSummary from(List<Review> reviews) {
        long enjoyment = 0;
        long difficulty = 0;
        long workload = 0;
        for (Review review : reviews) {
            enjoyment += review.enjoyment;
            difficulty += review.difficulty;
            workload += review.workload;
        }
        return new ReviewSummary(reviews.size(), enjoyment, difficulty, workload);
    }

    private static OptionalDouble average(long sum, int count) {
        return count == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) sum / count);
    }
}
