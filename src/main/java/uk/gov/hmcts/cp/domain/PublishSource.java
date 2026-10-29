package uk.gov.hmcts.cp.domain;

/** Which publish flow owns a {@code court_list_publish_status} row; part of the row's unique key. */
public enum PublishSource {
    /** The standard/online-public flow and SJP. */
    STANDARD,
    /** The Crown flow ({@code POST /crown/publish}): no PDF, so no file status. */
    CROWN
}
