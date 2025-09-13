// https://yandex.cloud/ru/docs/functions/concepts/function-invoke
package com.github.drewlakee.yabarsik

import yandex.cloud.sdk.functions.Context
import yandex.cloud.sdk.functions.YcFunction

class Request()

data class Response(val message: String) {
    object Status {
        val OK = Response("OK")
    }
}

class YcHandler : YcFunction<Request, Response> {
    override fun handle(request: Request, context: Context): Response {
        logInfo("My function executed.")
        return Response.Status.OK
    }
}