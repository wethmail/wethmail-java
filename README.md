# WethMail Java SDK

Java client for the WethMail v1 transactional email API (generator `java`). It
wraps the generated OpenAPI client with a small stable facade, typed errors and
retries with exponential backoff (and jitter) on transient failures.

Artifact: `com.wethmail:wethmail-java` (Maven / GitHub Packages).

## Install

```xml
<dependency>
  <groupId>com.wethmail</groupId>
  <artifactId>wethmail-java</artifactId>
  <version>1.0.0</version>
</dependency>
```

The package is published to GitHub Packages; see the repository's Packages tab
for the Maven repository coordinates.

## Quick start

```java
import com.wethmail.client.WethMailClient;
import com.wethmail.model.CreateEmailRequest;
import com.wethmail.model.EmailCreated;
import com.wethmail.model.Email;

WethMailClient client = new WethMailClient(
    System.getenv("WETHMAIL_API_KEY"), "https://api.example.com");

EmailCreated created = client.sendEmail(new CreateEmailRequest()
    .from("no-reply@mail.example.com")
    .to("customer@example.com")
    .subject("Confirm your address")
    .body("Hi, please confirm your email."));

Email email = client.getEmail(created.getId());
System.out.println("status: " + email.getStatus());
```

`sendEmail` enqueues the email and returns its id and initial status at once;
delivery is asynchronous. Poll `getEmail` until the status is `sent`, `failed`
or `bounced`. Ids are `java.util.UUID`.

## Configuration

`WethMailClient` constructors:

| Constructor | Effect |
|---|---|
| `WethMailClient(apiKey)` | Default base URL (`https://api.wethmail.local`) |
| `WethMailClient(apiKey, basePath)` | Explicit base URL |
| `WethMailClient(apiKey, basePath, maxRetries, baseDelayMs, maxDelayMs)` | Full control (defaults `2`, `200`, `5000`) |

## Errors

```java
import com.wethmail.client.WethMailClient.APIError;

try {
    client.getEmail(id);
} catch (APIError e) {
    System.err.printf("http %d: %s (request_id=%s)%n",
        e.getStatusCode(), e.getMessage(), e.getRequestId());
    if (e.isRetryable()) {
        // transient (network, 429 or 5xx)
    }
}
```

`getStatusCode()` is `0` on a network failure, `getMessage()` is the API `erro`
field and `getRequestId()` is the `X-Request-ID` header value.
