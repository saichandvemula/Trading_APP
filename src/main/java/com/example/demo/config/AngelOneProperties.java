package com.example.demo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "angel-one")
public record AngelOneProperties(
        String apiKey,
        String clientCode,
        String password,
        String totpSecret,
        String clientLocalIp,
        String clientPublicIp,
        String macAddress,
        @NotBlank String restBaseUrl,
        @NotBlank String websocketUrl,
        @Valid @NotNull OptionChain optionChain,
        @Valid @NotNull Websocket websocket
) {
    public record OptionChain(
            String niftyExpiry,
            String bankniftyExpiry,
            @Positive int strikeWindow
    ) {
    }

    public record Websocket(
            boolean enabled,
            @NotBlank String correlationId,
            int exchangeType,
            List<String> tokens
    ) {
    }
}
