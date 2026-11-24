package ratemycourse.usecase.search;

import java.util.List;

/** Successful empty pages and catalogue failures are different outcomes. */
public final class SearchResponse {
    private final List<CourseData> courses;
    private final int total;
    private final int page;
    private final int pageSize;
    private final String error;

    private SearchResponse(List<CourseData> courses, int total, int page, int pageSize, String error) {
        this.courses = List.copyOf(courses);
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.error = error;
    }

    public static SearchResponse success(List<CourseData> courses, int total, SearchRequest request) {
        return new SearchResponse(courses, total, request.getPage(), request.getPageSize(), "");
    }

    public static SearchResponse failure(String message, SearchRequest request) {
        return new SearchResponse(List.of(), 0, request.getPage(), request.getPageSize(), message);
    }

    public boolean isSuccess() { return error.isEmpty(); }
    public List<CourseData> getCourses() { return courses; }
    public int getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public boolean hasNextPage() { return (long) page * pageSize < total; }
    public String getError() { return error; }
}
