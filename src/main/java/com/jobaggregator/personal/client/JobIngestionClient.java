package com.jobaggregator.personal.client;

import com.jobaggregator.personal.model.JobOffer;
import com.jobaggregator.personal.model.JobSource;

import java.util.List;

public interface JobIngestionClient {

    JobSource getSource();

    List<JobOffer> fetchJobs();

    default String getDetailedStatus() {
        return null;
    }
}
