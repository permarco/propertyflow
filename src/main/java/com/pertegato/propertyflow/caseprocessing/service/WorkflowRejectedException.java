package com.pertegato.propertyflow.caseprocessing.service;

/**
 * A workflow contract violation must stop the job before any business effect.
 */
public class WorkflowRejectedException extends RuntimeException {

    public WorkflowRejectedException(String reason) {
        super(reason);
    }
}
