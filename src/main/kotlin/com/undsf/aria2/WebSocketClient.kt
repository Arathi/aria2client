package com.undsf.aria2

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeType
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.undsf.aria2.events.EventListener
import com.undsf.aria2.messages.SessionInfo
import com.undsf.aria2.messages.TaskStatus
import com.undsf.aria2.messages.Version
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage

private val logger = LoggerFactory.getLogger(WebSocketClient::class.java)

/**
 * Aria2的WebSocket客户端
 */
class WebSocketClient(
    ssl: Boolean = false,
    host: String = "127.0.0.1",
    port: UShort = 6800U,
    path: String = "/jsonrpc",
    secret: String? = null,
    val listener: EventListener? = null,
): Client(Protocol.of("ws", ssl), host, port, path, secret) {
    private lateinit var websocket: WebSocket
    private val mapper = jacksonObjectMapper()

    fun connect() {
        val httpClient = HttpClient.newHttpClient()
        val uri = URI.create(baseURL)
        val future = httpClient.newWebSocketBuilder()
            .buildAsync(uri, object : WebSocket.Listener {
                override fun onOpen(webSocket: WebSocket?) {
                    webSocket?.request(1)
                    connected = true
                    listener?.onConnected()
                }

                override fun onText(webSocket: WebSocket?, data: CharSequence?, last: Boolean): CompletionStage<*>? {
                    webSocket?.request(1)
                    logger.info("【JSON-RPC】接收到来自Aria2的文本报文：${data}")

                    if (data != null) {
                        val text = data.toString()
                        val tree = mapper.readTree(text)
                        if (tree == null) {
                            logger.warn("无法解析JSON-RPC报文：${text}")
                            return null
                        }

                        if (!tree.has("jsonrpc")) {
                            logger.warn("找不到jsonrpc字段，无法确认协议版本：${text}")
                            return null
                        }

                        if (tree.has("method")) {
                            val method = tree.get("method").textValue()
                            val paramsNode = tree.get("params")
                            onCallback(method, paramsNode as ArrayNode)
                            return null
                        }

                        var msgId: Any? = null
                        if (tree.has("id")) {
                            val msgIdNode = tree.get("id")
                            msgId = when (msgIdNode.nodeType) {
                                JsonNodeType.STRING -> msgIdNode.textValue()
                                JsonNodeType.NUMBER -> msgIdNode.longValue()
                                else -> {
                                    logger.warn("无法解析的id类型：${msgIdNode.nodeType}")
                                    null
                                }
                            }
                        }

                        if (tree.has("result")) {
                            val resultNode = tree.get("result")
                            onResultReceived(msgId, resultNode)
                            return null
                        }

                        if (tree.has("error")) {
                            val errorNode = tree.get("error")
                            onErrorReceived(msgId, errorNode)
                            return null
                        }

                        logger.warn("JSON-RPC报文缺少关键字段：${text}")
                        return null
                    }
                    return null
                }

                override fun onPing(webSocket: WebSocket?, message: ByteBuffer?): CompletionStage<*> {
                    webSocket?.request(1)
                    logger.debug("【JSON-RPC】ping")
                    return super.onPing(webSocket, message)
                }


                override fun onPong(webSocket: WebSocket?, message: ByteBuffer?): CompletionStage<*> {
                    webSocket?.request(1)
                    logger.debug("【JSON-RPC】pong")
                    return super.onPong(webSocket, message)
                }

                override fun onBinary(webSocket: WebSocket?, data: ByteBuffer?, last: Boolean): CompletionStage<*> {
                    logger.info("【JSON-RPC】接收到来自Aria2的二进制报文：${data}")
                    return super.onBinary(webSocket, data, last)
                }

                override fun onClose(webSocket: WebSocket?, statusCode: Int, reason: String?): CompletionStage<*>? {
                    logger.info("【JSON-RPC】Aria2连接正在关闭")
                    return super.onClose(webSocket, statusCode, reason)
                }

                override fun onError(webSocket: WebSocket?, error: Throwable?) {
                    logger.info("【JSON-RPC】Aria2连接发生错误")
                    super.onError(webSocket, error)
                }
            })
        websocket = future.join()
    }

    fun onCallback(method: String, params: JsonNode) {
        // logger.info("接收到${method}方法回调，参数如下：${params.textValue()}")
        val statusList: List<TaskStatus> = mapper.readValue(params.toString())
        val statusValue = when (method) {
            "aria2.onDownloadStart" -> TaskStatus.StatusValue.active
            "aria2.onDownloadPause" -> TaskStatus.StatusValue.paused
            "aria2.onDownloadComplete" -> TaskStatus.StatusValue.complete
            "aria2.onDownloadError" -> TaskStatus.StatusValue.error
            else -> {
                logger.warn("无法处理${method}回调")
                null
            }
        }
        if (statusValue != null) {
            statusList.forEach {
                val status = TaskStatus(
                    gid = it.gid,
                    status = statusValue
                )
                listener?.taskStatusUpdated(status)
            }
        }
    }

    fun onResultReceived(id: Any?, resultNode: JsonNode) {
        if (id == null) {
            logger.warn("报文id为空，无法获取响应报文类型")
            return
        }

        when (methods[id]) {
            "aria2.getVersion" -> {
                val version: Version = mapper.readValue(resultNode.toString())
                listener?.onGetVersion(version)
            }
            "aria2.getSessionInfo" -> {
                val session: SessionInfo = mapper.readValue(resultNode.toString())
                listener?.onGetSessionInfo(session)
            }
            null -> {
                logger.warn("无法获取请求${id}的方法类型，无法解析响应报文")
            }
            else -> {
                logger.warn("暂不支持方法${methods[id]}的响应报文解析")
            }
        }
    }

    fun onErrorReceived(id: Any?, errorNode: JsonNode) {
        logger.warn("发送请求${id}时出现异常，返回错误信息：${errorNode.textValue()}")
    }

    override fun call(method: String, params: List<Any>?, id: Any?) {
        val message = mutableMapOf<String, Any>()
        message["jsonrpc"] = "2.0"
        message["method"] = method

        // 生成params
        if (params != null) {
            message["params"] = params
        }

        // 生成id
        var msgId = id
        if (msgId == null) {
            msgId = sequence.incrementAndGet()
        }
        message["id"] = msgId
        // 记录方法名，用于解析响应报文
        methods[msgId] = method

        // 生成json，发送
        val json = mapper.writeValueAsString(message)
        logger.info("向Aria2服务端发送报文：${json}")
        val future = websocket.sendText(json, true)
        future.join()
    }
}