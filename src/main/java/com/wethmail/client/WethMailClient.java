/*
 * Façade de conveniência para o cliente WethMail gerado (java).
 *
 * Ficheiro de overlay (sdk/overlays/java): é copiado para o pacote gerado e
 * acrescenta o que o cliente gerado não tem — uma API estável, erros tipados
 * (`WethMailClient.APIError`) e retries com backoff para falhas transitórias
 * (rede, 429 e 5xx).
 */

package com.wethmail.client;

import com.wethmail.api.DomainsApi;
import com.wethmail.api.EmailsApi;
import com.wethmail.api.StatsApi;
import com.wethmail.model.CreateDomainRequest;
import com.wethmail.model.CreateEmailRequest;
import com.wethmail.model.Domain;
import com.wethmail.model.DomainVerifyResponse;
import com.wethmail.model.Email;
import com.wethmail.model.EmailCreated;
import com.wethmail.model.Error;
import com.wethmail.model.Stats;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WethMailClient {

    private static final String DEFAULT_BASE_PATH = "https://api.wethmail.local";

    /** Erro tipado devolvido por todos os métodos da façade. */
    public static final class APIError extends Exception {
        private final int statusCode;
        private final String requestId;

        public APIError(int statusCode, String message, String requestId) {
            super(message);
            this.statusCode = statusCode;
            this.requestId = requestId;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getRequestId() {
            return requestId;
        }

        /** Diz se a falha é transitória e vale a pena tentar de novo. */
        public boolean isRetryable() {
            return statusCode == 0 || statusCode == 429 || statusCode >= 500;
        }

        @Override
        public String toString() {
            String prefix = "wethmail: http " + statusCode + ": " + getMessage();
            return requestId.isEmpty() ? prefix : prefix + " (request_id=" + requestId + ")";
        }
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        T invoke() throws ApiException;
    }

    private final EmailsApi emails;
    private final DomainsApi domains;
    private final StatsApi statsApi;
    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;

    public WethMailClient(String apiKey) {
        this(apiKey, DEFAULT_BASE_PATH);
    }

    public WethMailClient(String apiKey, String basePath) {
        this(apiKey, basePath, 2, 200L, 5000L);
    }

    public WethMailClient(String apiKey, String basePath, int maxRetries, long baseDelayMs, long maxDelayMs) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(stripTrailingSlashes(basePath));
        apiClient.setBearerToken(apiKey);
        this.emails = new EmailsApi(apiClient);
        this.domains = new DomainsApi(apiClient);
        this.statsApi = new StatsApi(apiClient);
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
    }

    public EmailCreated sendEmail(CreateEmailRequest request) throws APIError {
        return call(() -> emails.createEmail(request));
    }

    public Email getEmail(UUID id) throws APIError {
        return call(() -> emails.getEmail(id));
    }

    public Domain createDomain(CreateDomainRequest request) throws APIError {
        return call(() -> domains.createDomain(request));
    }

    public List<Domain> listDomains() throws APIError {
        return call(() -> domains.listDomains());
    }

    public Domain getDomain(UUID id) throws APIError {
        return call(() -> domains.getDomain(id));
    }

    public DomainVerifyResponse verifyDomain(UUID id) throws APIError {
        return call(() -> domains.verifyDomain(id));
    }

    public void deleteDomain(UUID id) throws APIError {
        call(() -> {
            domains.deleteDomain(id);
            return null;
        });
    }

    public Stats stats(OffsetDateTime since) throws APIError {
        return call(() -> statsApi.getStats(since));
    }

    private <T> T call(ApiCall<T> call) throws APIError {
        int attempt = 0;
        for (;;) {
            try {
                return call.invoke();
            } catch (ApiException e) {
                APIError error = mapError(e);
                if (attempt >= maxRetries || !error.isRetryable()) {
                    throw error;
                }
                sleep(delayMs(attempt, e));
                attempt++;
            }
        }
    }

    private APIError mapError(ApiException e) {
        int statusCode = e.getCode();
        String message = e.getMessage();

        String body = e.getResponseBody();
        if (body != null && !body.isEmpty()) {
            try {
                Error parsed = JSON.getGson().fromJson(body, Error.class);
                if (parsed != null && parsed.getErro() != null && !parsed.getErro().isEmpty()) {
                    message = parsed.getErro();
                }
            } catch (RuntimeException ignored) {
                // corpo não-JSON: mantém a mensagem da excepção
            }
        }

        String requestId = headerValue(e.getResponseHeaders(), "x-request-id");
        return new APIError(statusCode, message == null ? "erro desconhecido" : message, requestId);
    }

    private long delayMs(int attempt, ApiException e) {
        String retryAfter = headerValue(e.getResponseHeaders(), "retry-after");
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                long seconds = Long.parseLong(retryAfter.trim());
                if (seconds >= 0) {
                    return seconds * 1000L;
                }
            } catch (NumberFormatException ignored) {
                // header não-numérico: usa backoff exponencial
            }
        }
        long delay = Math.min(baseDelayMs * (1L << attempt), maxDelayMs);
        return delay / 2 + (long) (ThreadLocalRandom.current().nextDouble() * (delay / 2.0));
    }

    private static String headerValue(Map<String, List<String>> headers, String name) {
        if (headers == null) {
            return "";
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (name.equalsIgnoreCase(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                String value = entry.getValue().get(0);
                return value == null ? "" : value;
            }
        }
        return "";
    }

    private static String stripTrailingSlashes(String basePath) {
        String result = basePath == null || basePath.isEmpty() ? DEFAULT_BASE_PATH : basePath;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
