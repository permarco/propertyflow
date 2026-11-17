package com.pertegato.propertyflow.caseprocessing.delivery;



public interface ReopenProcessGateway {

    boolean completed(long leader, String tenant);
}
