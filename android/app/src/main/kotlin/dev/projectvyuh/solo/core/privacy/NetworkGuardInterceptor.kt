package dev.projectvyuh.solo.core.privacy

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The OkHttp interceptor that enforces [NetworkPolicy].
 *
 * Install it as an APPLICATION interceptor (not a network interceptor) so
 * it sees the original URL the caller asked for. Network interceptors see
 * the URL after redirects — we want the original-call audit trail intact.
 *
 * Behavior:
 *   - Allowed → proceed; record decision.
 *   - Denied  → record decision and throw [NetworkGuardException] (an IOException
 *               so OkHttp/coroutines propagate it through the normal failure path).
 *
 * Redirects: OkHttp follows redirects internally. Each followed redirect target
 * is also passed through this interceptor by virtue of OkHttp re-entering the
 * interceptor chain for the new request. That's how a signed `.hf.co` redirect
 * from `huggingface.co` gets re-checked against the policy.
 */
@Singleton
class NetworkGuardInterceptor @Inject constructor(
    private val auditLog: NetworkAuditLog,
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val decision = NetworkPolicy.evaluate(request.url, request.method)

        auditLog.record(decision)

        when (decision) {
            is NetworkPolicy.Decision.Denied -> throw NetworkGuardException(decision)

            is NetworkPolicy.Decision.Allowed -> {
                // Per-purpose body validation: read-only purposes (model downloads,
                // future web-fetch tools, app updates) must NEVER carry a body.
                // Catches the entire class of "a bug turned a GET into a POST with
                // personal data in the body" without trying to classify content.
                if (decision.purpose.readOnly && request.body != null) {
                    val violation = NetworkPolicy.Decision.Denied(
                        reason = "purpose=${decision.purpose.name} is read-only but request carries a body",
                        host = decision.host,
                        method = decision.method,
                        path = decision.path,
                    )
                    auditLog.record(violation)
                    throw NetworkGuardException(violation)
                }
                return chain.proceed(request)
            }
        }
    }
}

/**
 * Thrown when an outbound request is blocked by [NetworkPolicy].
 *
 * Subclass of [IOException] so OkHttp surfaces it through the normal failure
 * path (vs. a runtime exception which would crash the process).
 */
class NetworkGuardException(
    val decision: NetworkPolicy.Decision.Denied,
) : IOException(
    "NetworkGuard blocked ${decision.method} ${decision.host}${decision.path} :: ${decision.reason}"
)
