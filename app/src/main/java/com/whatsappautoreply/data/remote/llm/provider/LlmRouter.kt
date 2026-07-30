package com.whatsappautoreply.data.remote.llm.provider

import com.whatsappautoreply.util.DebugLogger
import kotlinx.coroutines.delay

class LlmRouter(
    private val providers: List<LlmProvider>
) {
    companion object {
        private const val TAG = "LlmRouter"
        private const val MAX_FAILURES = 2
        private const val COOLDOWN_MS = 10 * 1000L // 10 seconds
    }

    private class ProviderState {
        var consecutiveFailures: Int = 0
        var unhealthyUntil: Long = 0L

        val isHealthy: Boolean
            get() = consecutiveFailures < MAX_FAILURES || System.currentTimeMillis() > unhealthyUntil
            
        fun recordFailure() {
            consecutiveFailures++
            if (consecutiveFailures >= MAX_FAILURES) {
                unhealthyUntil = System.currentTimeMillis() + COOLDOWN_MS
            }
        }
        
        fun recordSuccess() {
            consecutiveFailures = 0
            unhealthyUntil = 0L
        }
    }

    private val providerStates = providers.associateWith { ProviderState() }

    suspend fun executeWithRouting(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 5000,
        temperature: Double = 1.5
    ): String? {
        for (provider in providers) {
            val state = providerStates[provider]!!
            
            if (!state.isHealthy) {
                DebugLogger.logEvent(TAG, "PROVIDER_SKIPPED_UNHEALTHY", mapOf(
                    "provider" to provider.name,
                    "cooldown_remaining_sec" to (state.unhealthyUntil - System.currentTimeMillis()) / 1000
                ))
                continue
            }
            
            // If it was unhealthy but cooldown expired, perform health check
            if (state.consecutiveFailures >= MAX_FAILURES && System.currentTimeMillis() > state.unhealthyUntil) {
                DebugLogger.logEvent(TAG, "PROVIDER_RECOVERY_HEALTH_CHECK", mapOf("provider" to provider.name))
                val isRecovered = provider.checkHealth()
                if (isRecovered) {
                    state.recordSuccess()
                    DebugLogger.logEvent(TAG, "PROVIDER_RECOVERED", mapOf("provider" to provider.name))
                } else {
                    state.recordFailure()
                    DebugLogger.logEvent(TAG, "PROVIDER_RECOVERY_FAILED", mapOf("provider" to provider.name))
                    continue
                }
            }

            val startTime = System.currentTimeMillis()
            try {
                DebugLogger.logEvent(TAG, "PROVIDER_ATTEMPT", mapOf("provider" to provider.name, "model" to provider.modelName))
                val reply = provider.generateCompletion(systemPrompt, userPrompt, maxTokens, temperature)
                
                val latency = System.currentTimeMillis() - startTime
                DebugLogger.logEvent(TAG, "PROVIDER_SUCCESS", mapOf(
                    "provider" to provider.name,
                    "model" to provider.modelName,
                    "latency_ms" to latency
                ))
                
                state.recordSuccess()
                if (reply != null) {
                    return reply
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    throw e
                }
                
                val latency = System.currentTimeMillis() - startTime
                DebugLogger.logError(TAG, "PROVIDER_FAILED", e, mapOf(
                    "provider" to provider.name,
                    "model" to provider.modelName,
                    "latency_ms" to latency,
                    "error" to (e.message ?: "Unknown error")
                ))
                state.recordFailure()
                
                if (state.consecutiveFailures >= MAX_FAILURES) {
                    DebugLogger.logEvent(TAG, "PROVIDER_MARKED_UNHEALTHY", mapOf(
                        "provider" to provider.name,
                        "cooldown_minutes" to 10
                    ))
                }
            }
        }
        
        // All providers failed or were skipped
        DebugLogger.logEvent(TAG, "ALL_PROVIDERS_FAILED_OR_SKIPPED")
        return null
    }
}
