package com.iss.backend.web;

import com.iss.backend.entity.Application;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One step of the student's application status timeline.
 * state is one of: done, current, upcoming, failed.
 */
public record TimelineStep(String label, String state, String note) {

    public static List<TimelineStep> forApplication(Application app) {
        List<TimelineStep> steps = new ArrayList<>();

        if (app == null) {
            steps.add(new TimelineStep("Application Submitted", "current", "Complete and submit your application form."));
            steps.add(new TimelineStep("Application Reviewed", "upcoming", null));
            steps.add(new TimelineStep("Shortlisted", "upcoming", null));
            steps.add(new TimelineStep("Interview Pending", "upcoming", null));
            steps.add(new TimelineStep("Final Selection", "upcoming", null));
            return steps;
        }

        String s = app.getStatus();
        boolean reviewed = !Status.APPLIED.equals(s);
        boolean notQualified = Status.NOT_QUALIFIED.equals(s);
        boolean shortlisted = Status.PASSED_SHORTLIST.contains(s);
        boolean scheduled = Status.INTERVIEW_SCHEDULED.equals(s);
        boolean interviewed = Status.INTERVIEW_DONE.contains(s);

        steps.add(new TimelineStep("Application Submitted", "done",
                app.getApplicationDate() != null ? "Submitted on " + app.getApplicationDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : null));

        steps.add(new TimelineStep("Application Reviewed", reviewed ? "done" : "current",
                reviewed ? null : "Your application is waiting to be reviewed by the section head."));

        if (notQualified) {
            steps.add(new TimelineStep("Shortlisted", "failed",
                    "Your O/L results did not meet the selection conditions for this stream."));
        } else {
            steps.add(new TimelineStep("Shortlisted", shortlisted ? "done" : "upcoming", null));
        }

        if (interviewed) {
            steps.add(new TimelineStep("Interview Completed", "done", null));
        } else {
            steps.add(new TimelineStep("Interview Pending", scheduled ? "current" : "upcoming",
                    scheduled ? "Your interview has been scheduled. See Interview Details." : null));
        }

        if (Status.SELECTED.equals(s)) {
            steps.add(new TimelineStep("Final Selection", "done", "Congratulations! You have been selected."));
        } else if (Status.NOT_SELECTED.equals(s)) {
            steps.add(new TimelineStep("Final Selection", "failed", "You have not been selected on this occasion."));
        } else {
            steps.add(new TimelineStep("Final Selection", Status.INTERVIEWED.equals(s) ? "current" : "upcoming",
                    Status.INTERVIEWED.equals(s) ? "Final results will be published after the cutoff calculation." : null));
        }

        return steps;
    }
}
