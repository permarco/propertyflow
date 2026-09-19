package com.pertegato.propertyflow.helloworld.service;

import com.pertegato.propertyflow.helloworld.persistence.HelloCounter;
import com.pertegato.propertyflow.helloworld.persistence.HelloCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HelloService {

    private static final long COUNTER_ID = 1L;

    private final HelloCounterRepository repository;

    public HelloService(HelloCounterRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public HelloResponse incrementAndGet() {
        HelloCounter counter = repository.findById(COUNTER_ID)
                .orElseGet(() -> new HelloCounter(COUNTER_ID, 0L));

        counter.increment();
        HelloCounter saved = repository.save(counter);

        return new HelloResponse("Hello World", saved.getCount());
    }
}
