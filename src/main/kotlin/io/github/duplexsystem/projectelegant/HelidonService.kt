package io.github.duplexsystem.projectelegant

import io.helidon.webserver.*


internal class HelidonService(private val requestPath: String, configuration: ServerConfiguration) {
    private val jsExecutor: ConcurrentJsExecutor = ConcurrentJsExecutor(jsCode)
    private val configuration: ServerConfiguration
    fun init() {
        WebServer.create(
            configuration,
            Routing.builder()[requestPath, Handler { req: ServerRequest, res: ServerResponse ->
                val requestId: Int = try {
                    req.queryParams().first("request")
                        .map { s: String -> s.toInt() }
                        .orElse(42)
                } catch (e: NumberFormatException) {
                    res.send("Request id must be a number")
                    return@Handler
                }
                jsExecutor.callJavaScriptAsyncFunction(requestId)
                    .whenComplete { jsonResult: Any, ex: Throwable? ->
                        if (ex == null) {
                            res.send(jsonResult)
                        } else {
                            res.send("There was an error: " + ex.message)
                        }
                    }
            }].build()
        ).start()
    }

    companion object {
        private const val jsCode = ("(async function(requestId) {"
                + "  try {"
                + "    let data = await computeFromJava(requestId);"
                + "    return JSON.stringify({requestId: requestId, result: data});"
                + "  } catch (e) {"
                + "    return 'There was an error in JS-land! ' + e;"
                + "  }"
                + "})")
    }

    init {
        this.configuration = configuration
    }
}