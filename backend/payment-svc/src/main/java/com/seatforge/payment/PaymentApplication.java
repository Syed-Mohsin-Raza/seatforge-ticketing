package com.seatforge.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentApplication {

    static void main(String[] args) throws InterruptedException {
        SpringApplication.run(PaymentApplication.class, args);
        Thread.currentThread().join();
    }
}