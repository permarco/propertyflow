package com.pertegato.propertyflow.access.service;

@FunctionalInterface
public interface StaffCodeDelivery {

    void send(String email, String code);
}
