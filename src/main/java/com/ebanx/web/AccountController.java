package com.ebanx.web;

import com.ebanx.domain.AccountService;
import com.ebanx.domain.Event;
import com.ebanx.domain.EventParser;
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

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping(value = "/reset", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> reset() {
        accountService.reset();
        return ResponseEntity.ok("OK");
    }

    @GetMapping(value = "/balance", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> balance(
            @RequestParam(name = "account_id", required = false) String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return ResponseEntity.badRequest().body("Missing account_id query parameter");
        }

        Long balance = accountService.getBalance(accountId);
        if (balance == null) {
            return ResponseEntity.status(404).body("0");
        }
        return ResponseEntity.ok(String.valueOf(balance));
    }

    @PostMapping("/event")
    public ResponseEntity<Map<String, Object>> event(@RequestBody(required = false) Map<String, Object> body) {
        Event event = EventParser.parse(body);
        AccountService.EventResult result = accountService.apply(event);
        return ResponseEntity.status(201).body(result.toJson());
    }
}
