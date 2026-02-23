// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import com.embabel.agent.api.invocation.AgentInvocation
import com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration
import com.embabel.agent.core.AgentPlatform
import com.github.drewlakee.yabarsik.agents.VkCommunityContentManagerAgentResult
import com.github.drewlakee.yabarsik.configuration.EmbabelAgentsContextConfiguration
import com.github.drewlakee.yabarsik.configuration.EmbabelOpenAiModelsContextConfiguration
import com.github.drewlakee.yabarsik.configuration.YabarsikContextConfiguration
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
            .use { context ->
                val applicationConfiguration =
                    YandexS3Api
                        .http()
                        .getObject(
                            bucket = System.getenv("CONFIGURATION_S3_BUCKET"),
                            objectId = System.getenv("CONFIGURATION_S3_OBJECT_ID"),
                        ).orThrow()

                YamlPropertySourceLoader().run {
                    load("applicationProperties", ByteArrayResource(applicationConfiguration.readAllBytes()))
                        .forEach { source -> context.environment.propertySources.addLast(source) }
                }
                context.register(
                    YabarsikContextConfiguration::class.java,
                    EmbabelOpenAiModelsContextConfiguration::class.java,
                    EmbabelAgentsContextConfiguration::class.java,
                    AgentPlatformAutoConfiguration::class.java,
                )
                context.refresh()

                val agentPlatform = context.getBean(AgentPlatform::class.java)
                val result =
                    AgentInvocation
                        .create<VkCommunityContentManagerAgentResult>(agentPlatform)
                        .run(mapOf())
                        .last(VkCommunityContentManagerAgentResult::class.java)
            }
        return Response.Status.OK
    }
}
