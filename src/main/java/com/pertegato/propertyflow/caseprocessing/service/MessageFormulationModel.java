package com.pertegato.propertyflow.caseprocessing.service;

import reactor.core.publisher.Flux;

/**
 * Erhält ausschliesslich die bewusst eingegebene externe Sachanweisung.
 */
public interface MessageFormulationModel {

    Flux<String> formulate(String instruction);
}
