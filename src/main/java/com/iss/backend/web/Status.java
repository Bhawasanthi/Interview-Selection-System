package com.iss.backend.web;

import java.util.List;

/**
 * Application status values stored in application.status, in the order an
 * application moves through them.
 */
public final class Status {

    public static final String APPLIED = "APPLIED";
    public static final String NOT_QUALIFIED = "NOT_QUALIFIED";
    public static final String SHORTLISTED = "SHORTLISTED";
    public static final String INTERVIEW_SCHEDULED = "INTERVIEW_SCHEDULED";
    public static final String INTERVIEWED = "INTERVIEWED";
    public static final String SELECTED = "SELECTED";
    public static final String NOT_SELECTED = "NOT_SELECTED";

    // Interview.status values
    public static final String SCHEDULED = "SCHEDULED";
    public static final String COMPLETED = "COMPLETED";

    /** Everything that passed the O/L selection conditions. */
    public static final List<String> PASSED_SHORTLIST =
            List.of(SHORTLISTED, INTERVIEW_SCHEDULED, INTERVIEWED, SELECTED, NOT_SELECTED);

    /** Everything that has been interviewed and marked. */
    public static final List<String> INTERVIEW_DONE =
            List.of(INTERVIEWED, SELECTED, NOT_SELECTED);

    /** Status filter options shown on applicant lists. */
    public static final List<String> ALL =
            List.of(APPLIED, SHORTLISTED, NOT_QUALIFIED, INTERVIEW_SCHEDULED, INTERVIEWED, SELECTED, NOT_SELECTED);

    private Status() {}
}
