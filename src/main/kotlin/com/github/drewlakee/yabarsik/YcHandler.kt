// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration
import com.embabel.agent.core.AgentPlatform
import com.embabel.agent.core.ProcessOptions
import com.embabel.agent.core.Verbosity
import com.github.drewlakee.yabarsik.agents.VkCommunityContentManagerAgentResult
import com.github.drewlakee.yabarsik.configuration.EmbabelAgentsContextConfiguration
import com.github.drewlakee.yabarsik.configuration.EmbabelOpenAiModelsContextConfiguration
import com.github.drewlakee.yabarsik.configuration.YabarsikContextConfiguration
import com.github.drewlakee.yabarsik.telegram.chat.TelegramMessage
import com.github.drewlakee.yabarsik.telegram.chat.TelegramReportChat
import com.github.drewlakee.yabarsik.telegram.chat.appendNewLine
import com.github.drewlakee.yabarsik.yandex.function.YandexFunctionService
import com.github.drewlakee.yabarsik.yandex.s3.api.YandexS3Api
import com.github.drewlakee.yabarsik.yandex.s3.api.http
import dev.forkhandles.result4k.orThrow
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.io.ByteArrayResource
import yandex.cloud.sdk.functions.Context
import yandex.cloud.sdk.functions.YcFunction

class Request

data class Response(
    val message: String,
) {
    object Status {
        val OK = Response("OK")
    }
}

class YcHandler : YcFunction<Request, Response> {
    override fun handle(
        request: Request,
        context: Context,
    ): Response {
        AnnotationConfigApplicationContext()
            .use { springContext ->
                val applicationConfiguration =
                    YandexS3Api
                        .http()
                        .getObject(
                            bucket = System.getenv("CONFIGURATION_S3_BUCKET"),
                            objectId = System.getenv("CONFIGURATION_S3_OBJECT_ID"),
                        ).orThrow()

                YamlPropertySourceLoader().run {
                    load("applicationProperties", ByteArrayResource(applicationConfiguration.readAllBytes()))
                        .forEach { source -> springContext.environment.propertySources.addLast(source) }
                }
                springContext.register(
                    YabarsikContextConfiguration::class.java,
                    EmbabelOpenAiModelsContextConfiguration::class.java,
                    EmbabelAgentsContextConfiguration::class.java,
                    AgentPlatformAutoConfiguration::class.java,
                )
                springContext.refresh()

                val telegramReportChat = springContext.getBean(TelegramReportChat::class.java)
                val functionService = springContext.getBean(YandexFunctionService::class.java)
                val agentPlatform = springContext.getBean(AgentPlatform::class.java)

                val agentResult =
                    AgentInvocation
                        .builder(agentPlatform)
                        .options(
                            ProcessOptions(
                                verbosity =
                                    Verbosity(
                                        showPrompts = true,
                                        showLlmResponses = true,
                                    ),
                            ),
                        ).build(VkCommunityContentManagerAgentResult::class.java)
                        .run(mapOf())
                        .last(VkCommunityContentManagerAgentResult::class.java)

                when (agentResult) {
                    is VkCommunityContentManagerAgentResult.AchievedGoal -> {
                        telegramReportChat.sendMessage(
                            TelegramMessage.formatted {
                                append(agentResult.message)
                                appendNewLine()
                                append("[Логи вызова функции](${functionService.getTraceLink(context)})")
                            },
                        )
                    }

                    is VkCommunityContentManagerAgentResult.IntermediateResult -> {
                        telegramReportChat.sendMessage(
                            TelegramMessage.formatted {
                                append(agentResult.message)
                                appendNewLine()
                                append("[Логи вызова функции](${functionService.getTraceLink(context)})")
                            },
                        )
                    }

                    null -> {
                        telegramReportChat.sendMessage(
                            TelegramMessage.formatted {
                                append("Платформа агентов вернула пустой результат. Обрати внимание на ошибки!")
                                appendNewLine()
                                append("[Логи вызова функции](${functionService.getTraceLink(context)})")
                            },
                        )
                    }
                }
            }
        return Response.Status.OK
    }
}
