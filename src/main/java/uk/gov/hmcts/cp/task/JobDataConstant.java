package uk.gov.hmcts.cp.task;

/**
 * Constants for job data keys used when triggering and executing court list publish tasks.
 */
public final class JobDataConstant {

    public static final String COURT_LIST_ID = "courtListId";
    public static final String COURT_CENTRE_ID = "courtCentreId";
    public static final String COURT_LIST_TYPE = "courtListType";
    public static final String PUBLISH_DATE = "publishDate";
    public static final String USER_ID = "userId";

    private JobDataConstant() {
        // utility class
    }
}
