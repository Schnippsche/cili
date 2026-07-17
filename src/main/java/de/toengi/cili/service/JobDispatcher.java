package de.toengi.cili.service;

import de.toengi.cili.model.entity.ProcessingJob;

public interface JobDispatcher {
    void dispatch(ProcessingJob job);
}
