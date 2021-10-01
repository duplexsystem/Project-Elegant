package io.github.duplexsystem.projectelegant

import io.helidon.webserver.ServerConfiguration

object JsJavaAsyncHelidonService {
    private const val ROUTE = "/greet"
    private const val PORT = 8080
    @JvmStatic
    fun main(args: Array<String>) {
        val configuration = ServerConfiguration.builder().port(PORT).build()
        HelidonService(ROUTE, configuration).init()
    }
}