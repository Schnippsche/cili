package de.toengi.cili.controller;

import de.toengi.cili.exception.CiliException;
import de.toengi.cili.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/public/customers/unsubscribe")
@RequiredArgsConstructor
public class CustomerUnsubscribeController {

    private final CustomerService customerService;
    private final TemplateEngine templateEngine;

    @GetMapping("/{token}")
    public ResponseEntity<String> confirm(@PathVariable String token) {
        try {
            customerService.verifyToken(token);
            Context ctx = new Context();
            ctx.setVariable("token", token);
            return html(templateEngine.process("customer/unsubscribe-confirm", ctx));
        } catch (CiliException e) {
            return html(templateEngine.process("customer/unsubscribe-invalid", new Context()));
        }
    }

    @PostMapping("/{token}")
    public ResponseEntity<String> unsubscribe(@PathVariable String token) {
        try {
            customerService.unsubscribe(token);
            return html(templateEngine.process("customer/unsubscribe-done", new Context()));
        } catch (CiliException e) {
            return html(templateEngine.process("customer/unsubscribe-invalid", new Context()));
        }
    }

    private ResponseEntity<String> html(String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(body);
    }
}
