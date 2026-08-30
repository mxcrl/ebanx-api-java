package com.ebanx.controller;

import com.ebanx.dto.Event;
import com.ebanx.dto.EventParser;
import com.ebanx.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Transport layer: translates HTTP requests into calls on the domain
 * layer and its outcomes into responses. Contains no business rules of
 * its own - those all live in AccountService and EventParser.
 */
@RestController
public final class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping(value = "/reset", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> reset() {
        log.info("POST /reset");
        accountService.reset();
        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/balance", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> balance(
            @RequestParam(name = "account_id", required = false) String accountId) {
        log.info("GET /balance account_id={}", accountId);
        if (accountId == null || accountId.isBlank()) {
            log.warn("GET /balance rejected: missing account_id query parameter");
            return ResponseEntity.badRequest().body("Missing account_id query parameter");
        }

        Long balance = accountService.getBalance(accountId);
        if (balance == null) {
            log.info("GET /balance account_id={} not found", accountId);
            return ResponseEntity.status(404).body("0");
        }
        log.debug("GET /balance account_id={} balance={}", accountId, balance);
        return ResponseEntity.ok(String.valueOf(balance));
    }

    @PostMapping("/event")
    public ResponseEntity<Map<String, Object>> event(@RequestBody(required = false) Map<String, Object> body) {
        log.info("POST /event body={}", body);
        Event event = EventParser.parse(body);
        AccountService.EventResult result = accountService.apply(event);
        Map<String, Object> json = result.toJson();
        log.info("POST /event -> 201 {}", json);
        return ResponseEntity.status(201).body(json);
    }
}
