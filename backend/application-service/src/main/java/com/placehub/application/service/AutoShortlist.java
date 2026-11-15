package com.placehub.application.service;

import com.placehub.application.client.ClientDtos;

import java.math.BigDecimal;

/** Pure auto-shortlist decision (spec 20.4): an AUTO_SHORTLIST job and a snapshot meeting the effective criteria. */
public final class AutoShortlist {

    public static final String REASON = "Met the auto-shortlist criteria";

    private AutoShortlist() {
    }

    public static boolean qualifies(ClientDtos.JobSummary job, BigDecimal cgpa, int backlogCount) {
        if (job == null || !"AUTO_SHORTLIST".equals(job.screeningMode()) || cgpa == null) {
            return false;
        }
        boolean cgpaOk = job.autoShortlistMinCgpa() == null || cgpa.compareTo(job.autoShortlistMinCgpa()) >= 0;
        boolean backlogsOk = job.autoShortlistMaxBacklogs() == null || backlogCount <= job.autoShortlistMaxBacklogs();
        return cgpaOk && backlogsOk;
    }
}
