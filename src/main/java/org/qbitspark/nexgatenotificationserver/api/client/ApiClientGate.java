package org.qbitspark.nexgatenotificationserver.api.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.qbitspark.nexgatenotificationserver.api.payload.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiClientGate {

    private final WebClient webClient;
    private static final int TIMEOUT = 60000;

    public <T, R> ApiResponse<R> post(String url, T body, Map<String, String> headers, Class<R> responseType) {
        try {
            log.info("POST: {}", url);

            R data = webClient.post()
                    .uri(url)
                    .headers(h -> addHeaders(h, headers))
                    .bodyValue(body != null ? body : "")
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(Duration.ofMillis(TIMEOUT))
                    .block();

            return ApiResponse.<R>builder()
                    .success(true)
                    .data(data)
                    .statusCode(200)
                    .build();

        } catch (Exception e) {
            log.error("POST failed: {}", url, e);
            return ApiResponse.<R>builder()
                    .success(false)
                    .errorMessage(getErrorMessage(e))
                    .statusCode(getStatusCode(e))
                    .build();
        }
    }

    public <T> ApiResponse<T> get(String url, Map<String, String> headers, Class<T> responseType) {
        try {
            log.info("GET: {}", url);

            T data = webClient.get()
                    .uri(url)
                    .headers(h -> addHeaders(h, headers))
                    .retrieve()
                    .bodyToMono(responseType)
                    .timeout(Duration.ofMillis(TIMEOUT))
                    .block();

            return ApiResponse.<T>builder()
                    .success(true)
                    .data(data)
                    .statusCode(200)
                    .build();

        } catch (Exception e) {
            log.error("GET failed: {}", url, e);
            return ApiResponse.<T>builder()
                    .success(false)
                    .errorMessage(getErrorMessage(e))
                    .statusCode(getStatusCode(e))
                    .build();
        }
    }

    private void addHeaders(HttpHeaders httpHeaders, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
    }

    private String getErrorMessage(Throwable e) {
        if (e instanceof WebClientResponseException wcre) {
            return String.format("HTTP %d: %s", wcre.getStatusCode().value(), wcre.getResponseBodyAsString());
        }
        return e.getMessage() != null ? e.getMessage() : "Unknown error";
    }

    private int getStatusCode(Throwable e) {
        if (e instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().value();
        }
        return 500;
    }
}