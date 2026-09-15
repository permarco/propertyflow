package com.pertegato.propertyflow.helloworld.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pertegato.propertyflow.helloworld.service.HelloResponse;
import com.pertegato.propertyflow.helloworld.service.HelloService;
import org.junit.jupiter.api.Test;

class HelloControllerTest {

    @Test
    void helloReturnsHelloWorldWithCounter() {
        HelloService service = mock(HelloService.class);
        when(service.incrementAndGet()).thenReturn(new HelloResponse("Hello World", 7L));

        HelloController controller = new HelloController(service);

        HelloResponse response = controller.hello();

        assertEquals("Hello World", response.message());
        assertEquals(7L, response.count());
    }
}
