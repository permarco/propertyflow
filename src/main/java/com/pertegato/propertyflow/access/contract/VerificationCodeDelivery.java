package com.pertegato.propertyflow.access.contract;

public interface VerificationCodeDelivery {
    void send(String email, String code);
}
