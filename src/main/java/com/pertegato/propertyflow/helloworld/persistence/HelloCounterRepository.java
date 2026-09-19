package com.pertegato.propertyflow.helloworld.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HelloCounterRepository extends JpaRepository<HelloCounter, Long> {
}
