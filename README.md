# WethMail Java SDK

Java client for the WethMail v1 transactional email API. It wraps the generated OpenAPI client with a stable facade, typed errors, and retries with exponential backoff and jitter on transient failures.

**Artifact:** `com.wethmail:wethmail-java` (Maven / GitHub Packages)

## Table of Contents

- [Installation](#installation)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [Error Handling](#error-handling)
- [Requirements](#requirements)
- [License](#license)

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.wethmail</groupId>
    <artifactId>wethmail-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

> **Note:** The package is published to GitHub Packages. See the repository's **Packages** tab for the Maven repository coordinates and authentication requirements.

## Quick Start

```java
import com.wethmail.client.WethMailClient;
import com.wethmail.model.CreateEmailRequest;
import com.wethmail.model.EmailCreated;
import com.wethmail.model.Email;

WethMailClient client = new WethMailClient(
    System.getenv("WETHMAIL_API_KEY"),
    "https://api.example.com"
);

EmailCreated created = client.sendEmail(new CreateEmailRequest()
    .from("no-reply@mail.example.com")
    .to("customer@example.com")
    .subject("Confirm your address")
    .body("Hi, please confirm your email.")
);

Email email = client.getEmail(created.getId());
System.out.println("status: " + email.getStatus());
```

`sendEmail` enqueues the email and immediately returns its ID and initial status. Delivery is **asynchronous**.

Poll `getEmail` until the status is `sent`, `failed`, or `bounced`. Email IDs are represented by `java.util.UUID`.

## Configuration

`WethMailClient` supports the following constructors:

| Constructor | Description |
| --- | --- |
| `WethMailClient(apiKey)` | Uses the default base URL: `https://api.wethmail.local` |
| `WethMailClient(apiKey, basePath)` | Specifies a custom base URL |
| `WethMailClient(apiKey, basePath, maxRetries, baseDelayMs, maxDelayMs)` | Configures retries and exponential backoff |

The full constructor defaults to **2 retries**, a base delay of **200 ms**, and a maximum delay of **5000 ms** when using the documented defaults.

> **Security:** Store your API key in an environment variable rather than hardcoding it in your source code.

```bash
export WETHMAIL_API_KEY="your-api-key"
```

## Error Handling

The SDK exposes `APIError` for API and network errors.

```java
import com.wethmail.client.WethMailClient.APIError;

try {
    client.getEmail(id);
} catch (APIError e) {
    System.err.printf(
        "http %d: %s (request_id=%s)%n",
        e.getStatusCode(),
        e.getMessage(),
        e.getRequestId()
    );

    if (e.isRetryable()) {
        // Handle a transient failure.
    }
}
```

The error methods provide the following information:

- `getStatusCode()` returns the HTTP status code, or `0` for a network failure.
- `getMessage()` returns the API error message.
- `getRequestId()` returns the value of the `X-Request-ID` response header, when available.
- `isRetryable()` indicates whether the error is considered transient and potentially retryable.

The SDK retries transient failures using exponential backoff with jitter, according to its configured retry policy.

## Requirements

- Java version compatible with the SDK's build configuration
- Maven for dependency management
- A valid WethMail API key
- Access to a WethMail API endpoint

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.