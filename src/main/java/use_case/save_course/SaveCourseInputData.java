package use_case.save_course;



/** Raw immutable request; validation is handled by the interactor. */
public final class SaveCourseInputData {
    private final String profileId;
    private final String courseCode;

    public SaveCourseInputData(String profileId, String courseCode) {
        this.profileId = profileId;
        this.courseCode = courseCode;
    }

    public String getProfileId() { return profileId; }
    public String getCourseCode() { return courseCode; }
}
