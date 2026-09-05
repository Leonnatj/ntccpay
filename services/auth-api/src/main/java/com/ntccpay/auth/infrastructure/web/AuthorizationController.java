package com.ntccpay.auth.infrastructure.web;

import com.ntccpay.auth.application.port.in.AuthorizationCommand;
import com.ntccpay.auth.application.port.in.AuthorizationResult;
import com.ntccpay.auth.application.port.in.RequestAuthorization;
import com.ntccpay.auth.infrastructure.web.dto.AuthorizationRequestDto;
import com.ntccpay.auth.infrastructure.web.dto.AuthorizationResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/authorizations")
public class AuthorizationController {

    private final RequestAuthorization requestAuthorization;

    public AuthorizationController(RequestAuthorization requestAuthorization) {
        this.requestAuthorization = requestAuthorization;
    }

    @PostMapping
    public ResponseEntity<AuthorizationResponseDto> authorize(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody AuthorizationRequestDto request) {

        AuthorizationResult result = requestAuthorization.handle(new AuthorizationCommand(
                request.pan(),
                request.amountMinor(),
                request.currency(),
                request.merchant(),
                idempotencyKey));

        var status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(AuthorizationResponseDto.from(result));
    }
}
