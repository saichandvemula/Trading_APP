package com.example.demo.service;

import com.example.demo.config.AngelOneProperties;
import com.example.demo.exception.SmartApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class SmartApiClient {

    private final RestTemplate restTemplate;
    private final AngelOneProperties properties;

    public SmartApiClient(RestTemplate restTemplate, AngelOneProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public JsonNode login(String clientCode, String password, String totp) {
        Map<String, String> body = new HashMap<>();
        body.put("clientcode", clientCode);
        body.put("password", password);
        body.put("totp", totp);
        return post("/rest/auth/angelbroking/user/v1/loginByPassword", body, null);
    }

    public JsonNode optionGreek(String jwtToken, String name, String expiryDate) {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("expirydate", expiryDate);
        return post("/rest/secure/angelbroking/marketData/v1/optionGreek", body, jwtToken);
    }

    public JsonNode candleData(
            String jwtToken,
            String exchange,
            String symbolToken,
            String interval,
            String fromDate,
            String toDate
    ) {
        Map<String, String> body = new HashMap<>();
        body.put("exchange", exchange);
        body.put("symboltoken", symbolToken);
        body.put("interval", interval);
        body.put("fromdate", fromDate);
        body.put("todate", toDate);
        return post("/rest/secure/angelbroking/historical/v1/getCandleData", body, jwtToken);
    }

    private JsonNode post(String path, Object body, String jwtToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0");
        headers.set("X-UserType", "USER");
        headers.set("X-SourceID", "WEB");
        headers.set("X-ClientLocalIP", properties.clientLocalIp());
        headers.set("X-ClientPublicIP", properties.clientPublicIp());
        headers.set("X-MACAddress", properties.macAddress());
        headers.set("X-PrivateKey", properties.apiKey());
        if (jwtToken != null && !jwtToken.isBlank()) {
            headers.setBearerAuth(jwtToken.replace("Bearer ", ""));
        }

        try {
            JsonNode response = restTemplate.postForObject(
                    properties.restBaseUrl() + path,
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );
            if (response == null) {
                throw new SmartApiException("SmartAPI returned an empty response");
            }
            if (response.has("status") && !response.path("status").asBoolean()) {
                throw new SmartApiException(response.path("message").asText("SmartAPI request failed"));
            }
            return response;
        } catch (RestClientException ex) {
            throw new SmartApiException("SmartAPI request failed: " + ex.getMessage(), ex);
        }
    }
}
