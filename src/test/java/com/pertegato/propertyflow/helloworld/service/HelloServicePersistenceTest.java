package com.pertegato.propertyflow.helloworld.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pertegato.propertyflow.helloworld.persistence.HelloCounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HelloServicePersistenceTest {

    @Autowired
    private HelloService helloService;

    @Autowired
    private HelloCounterRepository repository;

    @BeforeEach
    void resetCounter() {
        repository.deleteAll();
    }

    @Test
    void incrementsCounterUsingFlywayManagedSchema() {
        HelloResponse first = helloService.incrementAndGet();
        HelloResponse second = helloService.incrementAndGet();

        assertEquals("Hello World", first.message());
        assertEquals(1L, first.count());
        assertEquals(2L, second.count());
        assertEquals(1L, repository.count());
    }
}
