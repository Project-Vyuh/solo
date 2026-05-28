package dev.projectvyuh.solo.core.privacy

import okhttp3.HttpUrl

/**
 * Solo's network egress policy: an enumerated, default-deny allow-list.
 *
 * Solo's core promise is that personal data never leaves the device. This file
 * is the architectural enforcement of that promise — every outbound HTTP
 * request must match one of the entries in [allowedDestinations]. There is no
 * "default allow" path. Adding a new destination here is a deliberate decision
 * that must be justified by the [AllowedDestination.purpose] field.
 *
 * What this layer protects against:
 *   - Accidental leakage via a misconfigured library
 *   - Telemetry / crash reporters added by transitive dependencies
 *   - A future agent tool inadvertently calling a non-allowlisted endpoint
 *
 * What this layer does NOT protect against:
 *   - Native code making syscalls directly. NetworkGuard sits at the OkHttp
 *     layer; raw socket usage would bypass it. We mitigate by (a) not using
 *     raw sockets ourselves, and (b) Android's Network Security Config which
 *     blocks cleartext by default.
 *   - Payload-level leaks within an allowed destination. This is by design:
 *     destination filtering is a clean, auditable line. Content inspection
 *     would require knowing what "personal data" looks like, which is
 *     undecidable in the general case.
 */
object NetworkPolicy {

    val allowedDestinations: List<AllowedDestination> = listOf(

        // Model downloads. Hugging Face hosts the GGUF weights for Solo's primary
        // model (Gemma 3n E4B). Read-only GET; we never upload to HF.
        AllowedDestination(
            host         = "huggingface.co",
            allowMethods = setOf("GET", "HEAD"),
            purpose      = Purpose.MODEL_DOWNLOAD,
            justification = "GGUF model weights for on-device inference",
        ),

        // Hugging Face uses CAS-bridge / Cloudfront for actual blob delivery.
        // The redirect target is signed and time-bound, so subdomain wildcards
        // are intentional but scoped to the bridge subdomain.
        AllowedDestination(
            hostSuffix   = ".hf.co",
            allowMethods = setOf("GET", "HEAD"),
            purpose      = Purpose.MODEL_DOWNLOAD,
            justification = "Hugging Face blob CDN (signed redirect target)",
        ),

        // GitHub releases — for future sideload-update channel. Solo distributes
        // via GitHub releases, not the Play Store; we may surface a "new build
        // available" indicator in-app.
        AllowedDestination(
            host         = "api.github.com",
            pathPrefix   = "/repos/Project-Vyuh/solo/releases",
            allowMethods = setOf("GET"),
            purpose      = Purpose.APP_UPDATES,
            justification = "Self-distributed release metadata only",
        ),
    )

    /**
     * Evaluate a URL against the policy. Returns a [Decision] describing
     * whether the request is allowed and which rule matched (or why it failed).
     */
    fun evaluate(url: HttpUrl, method: String): Decision {
        val host = url.host
        val path = url.encodedPath
        val upperMethod = method.uppercase()

        // HTTPS-only. We don't run a Network Security Config exception for
        // cleartext, but checking here gives a clearer audit-log message.
        if (!url.isHttps) {
            return Decision.Denied(
                reason = "cleartext (non-HTTPS) requests are forbidden",
                host = host,
                method = upperMethod,
                path = path,
            )
        }

        for (dest in allowedDestinations) {
            if (!dest.matchesHost(host)) continue
            if (!dest.matchesPath(path)) continue
            if (upperMethod !in dest.allowMethods) {
                return Decision.Denied(
                    reason = "method $upperMethod not permitted for ${dest.summary}",
                    host = host,
                    method = upperMethod,
                    path = path,
                )
            }
            return Decision.Allowed(
                purpose = dest.purpose,
                reason = dest.purpose.name,
                host = host,
                method = upperMethod,
                path = path,
            )
        }

        return Decision.Denied(
            reason = "host not in allow-list",
            host = host,
            method = upperMethod,
            path = path,
        )
    }

    enum class Purpose(val readOnly: Boolean) {
        // readOnly=true means: requests under this purpose MUST NOT carry a request
        // body. NetworkGuardInterceptor enforces this. The intent is to catch the
        // failure mode where a bug accidentally turns a GET into a POST with
        // personal data in the body. Read-only purposes use Range/HEAD/GET only.
        MODEL_DOWNLOAD(readOnly = true),
        APP_UPDATES(readOnly = true),
        WEB_FETCH_TOOL(readOnly = true),  // future: agent reads public web content
    }

    sealed interface Decision {
        val host: String
        val method: String
        val path: String
        val reason: String

        data class Allowed(
            val purpose: Purpose,
            override val reason: String,
            override val host: String,
            override val method: String,
            override val path: String,
        ) : Decision

        data class Denied(
            override val reason: String,
            override val host: String,
            override val method: String,
            override val path: String,
        ) : Decision
    }
}

/**
 * One allow-list entry. Match against either an exact [host] OR a [hostSuffix]
 * (which matches any subdomain — useful for CDN deliveries where the exact
 * host name varies per request).
 */
data class AllowedDestination(
    val host: String? = null,
    val hostSuffix: String? = null,
    val pathPrefix: String? = null,
    val allowMethods: Set<String>,
    val purpose: NetworkPolicy.Purpose,
    val justification: String,
) {
    init {
        require(host != null || hostSuffix != null) {
            "AllowedDestination needs either host or hostSuffix"
        }
    }

    fun matchesHost(actual: String): Boolean {
        if (host != null && actual.equals(host, ignoreCase = true)) return true
        if (hostSuffix != null && actual.endsWith(hostSuffix, ignoreCase = true)) return true
        return false
    }

    fun matchesPath(actualPath: String): Boolean =
        pathPrefix == null || actualPath.startsWith(pathPrefix)

    val summary: String
        get() = (host ?: "*$hostSuffix") + (pathPrefix ?: "")
}
