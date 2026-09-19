package com.pertegato.propertyflow.helloworld.controller;

import com.pertegato.propertyflow.helloworld.service.HelloResponse;
import com.pertegato.propertyflow.helloworld.service.HelloService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

@RestController
public class HelloController {

    private final HelloService helloService;

    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping({"/hello", "/api/hello"})
    public HelloResponse hello() {
        return helloService.incrementAndGet();
    }

    @GetMapping("/hello-page")
    public ResponseEntity<String> helloPage() {
        HelloResponse response = helloService.incrementAndGet();
        String safeMessage = HtmlUtils.htmlEscape(response.message());
        String html = """
                <!DOCTYPE html>
                <html lang=\"de\">
                <head>
                    <meta charset=\"UTF-8\"/>
                    <title>Hello World</title>
                    <style>
                        body { font-family: Arial, sans-serif; background: #f4f7fb; color: #1f2937; display: grid; place-items: center; min-height: 100vh; margin: 0; }
                        .card { background: white; padding: 2rem 3rem; border-radius: 16px; box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12); text-align: center; }
                        h1 { color: #2563eb; margin-bottom: 0.5rem; }
                        p { font-size: 1.1rem; }
                        strong { font-size: 2rem; color: #0f172a; }
                    </style>
                </head>
                <body>
                    <div class=\"card\">
                        <h1>%s</h1>
                        <p>Diese Seite wurde bereits <strong>%d</strong> Mal aufgerufen.</p>
                    </div>
                </body>
                </html>
                """.formatted(safeMessage, response.count());

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
