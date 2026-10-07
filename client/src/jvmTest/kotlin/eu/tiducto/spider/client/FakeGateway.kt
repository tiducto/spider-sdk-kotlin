package eu.tiducto.spider.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * A local JDK HTTP server standing in for the gateway, so the public calls run end to end on the real engine.
 * Records every request and answers each path with its [Reply] (404 otherwise).
 */
internal class FakeGateway : AutoCloseable {

    data class Seen(
        val path: String,
        val body: String,
        val accept: List<String> = emptyList(),
        val method: String = "",
        val query: String? = null,
    )

    class Reply(val status: Int, val contentType: String, val body: String)

    val seen = CopyOnWriteArrayList<Seen>()

    @Volatile
    var replies: Map<String, Reply> = emptyMap()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val accept = exchange.requestHeaders["Accept"].orEmpty()
            seen += Seen(
                path,
                exchange.requestBody.readBytes().decodeToString(),
                accept,
                exchange.requestMethod,
                exchange.requestURI.rawQuery,
            )
            val reply = replies[path] ?: Reply(404, "application/json", "{}")
            val bytes = reply.body.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", reply.contentType)
            exchange.sendResponseHeaders(reply.status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val baseUrl = "http://127.0.0.1:${server.address.port}"

    /** The JSON body of the only request seen. */
    fun body(): JsonObject = Json.parseToJsonElement(seen.single().body).jsonObject

    override fun close() = server.stop(0)

    companion object {
        fun json(body: String) = Reply(200, "application/json", body)

        fun events(body: String) = Reply(200, "text/event-stream", body)
    }
}
