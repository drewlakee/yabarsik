package com.github.drewlakee.yabarsik.configuration

import com.embabel.agent.openai.OpenAiChatOptionsConverter
import com.embabel.agent.openai.OpenAiCompatibleModelFactory
import com.embabel.agent.spi.LlmService
import com.embabel.agent.spi.support.springai.SpringAiLlmService
import com.embabel.common.ai.model.PricingModel
import org.springframework.ai.retry.TransientAiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.retry.RetryCallback
import org.springframework.retry.RetryContext
import org.springframework.retry.RetryListener
import org.springframework.retry.support.RetryTemplate
import org.springframework.web.client.ResourceAccessException
import java.time.Duration

@Configuration
open class EmbabelOpenAiModelsContextConfiguration(
    @Value("\${yabarsik.openai.base-url}")
    baseUrl: String,
    @Value("\${yabarsik.openai.api-key}")
    apiKey: String,
) : OpenAiCompatibleModelFactory(
        baseUrl = baseUrl,
        apiKey = apiKey,
        completionsPath = null,
        embeddingsPath = null,
    ) {
    @Bean
    open fun genericModel(
        @Value("\${yabarsik.openai.base-url}")
        baseUrl: String,
        @Value("\${yabarsik.openai.generic-model.name}")
        openAiProviderModelName: String,
        @Value("\${yabarsik.openai.generic-model.retry.max-attempts}")
        maxAttempts: Int,
        @Value("\${yabarsik.openai.generic-model.retry.backoff-initial-interval-millis}")
        backoffInitialIntervalMs: Long,
        @Value("\${yabarsik.openai.generic-model.retry.backoff-multiplier}")
        backoffMultiplier: Double,
        @Value("\${yabarsik.openai.generic-model.retry.backoff-max-interval-millis}")
        backoffMaxIntervalMs: Long,
    ): LlmService<*> =
        SpringAiLlmService(
            name = YabarsikLlmModels.GENERIC_MODEL.modelName,
            chatModel =
                chatModelOf(
                    model = openAiProviderModelName,
                    retryTemplate =
                        RetryTemplate
                            .builder()
                            .maxAttempts(maxAttempts)
                            .retryOn(TransientAiException::class.java)
                            .retryOn(ResourceAccessException::class.java)
                            .exponentialBackoff(
                                Duration.ofMillis(backoffInitialIntervalMs),
                                backoffMultiplier,
                                Duration.ofMillis(backoffMaxIntervalMs),
                            ).withListener(
                                object : RetryListener {
                                    override fun <T, E : Throwable?> onError(
                                        context: RetryContext,
                                        callback: RetryCallback<T?, E?>?,
                                        throwable: Throwable?,
                                    ) {
                                        logger.warn("OpenAI client retry error. Retry count:{}", context.retryCount, throwable)
                                    }
                                },
                            ).build(),
                ),
            provider = "<$baseUrl>:<$openAiProviderModelName>",
            optionsConverter = OpenAiChatOptionsConverter,
            pricingModel = PricingModel.ALL_YOU_CAN_EAT,
            knowledgeCutoffDate = null,
        )
}
