package com.pertegato.propertyflow.helloworld.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hello_counter")
public class HelloCounter {

    @Id
    private Long id = 1L;

    @Column(nullable = false)
    private long count = 0L;

    public HelloCounter() {
    }

    public HelloCounter(Long id, long count) {
        this.id = id;
        this.count = count;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public void increment() {
        this.count++;
    }
}
