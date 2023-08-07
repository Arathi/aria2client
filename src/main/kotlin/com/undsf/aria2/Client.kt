package com.undsf.aria2

import com.undsf.aria2.messages.Options
import java.util.concurrent.atomic.AtomicLong

abstract class Client(
    protected val protocol: Protocol,
    protected val host: String,
    protected val port: UShort,
    protected val path: String,
    protected val secret: String?
) {
    /**
     * 拼接JSON-RPC服务地址
     */
    protected val baseURL: String get() {
        val sep = if (!path.startsWith("/")) "/" else ""
        return "${protocol.name}://${host}:${port}${sep}$path"
    }

    /**
     * 报文序列
     */
    protected val sequence = AtomicLong(System.currentTimeMillis() * 1000000)

    /**
     * 生成token
     */
    protected val token: String? get() = if (secret != null) "token:${secret}" else null

    /**
     * 请求方法名缓存
     */
    protected val methods = mutableMapOf<Any, String>()

    /**
     * 是否连接成功
     */
    var connected: Boolean = false
        protected set

    enum class Protocol {
        http,
        https,
        ws,
        wss;
        companion object {
            fun of(base: String, ssl: Boolean): Protocol {
                val protocolName = StringBuilder(base)
                if (ssl) protocolName.append("s")
                return Protocol.valueOf(protocolName.toString())
            }
        }
    }

    protected fun buildParams(vararg args: Any?): List<Any>? {
        val list = mutableListOf<Any>()
        // 添加token
        if (token != null) {
            list.add(token!!)
        }
        // 添加非空参数
        args.forEach { arg ->
            if (arg != null) {
                list.add(arg)
            }
        }
        // 添加后仍然没有参数，直接返回null
        if (list.isEmpty()) return null
        return list
    }

    /**
     * 发送
     */
    abstract fun call(method: String, params: List<Any>? = buildParams(), id: Any? = null)

    /**
     * 提交下载任务
     */
    fun addUri(uris: List<String>, options: Options = Options(), position: Int? = null, id: Any? = null) {
        val params = buildParams(uris, options.toMap(), position)
        call("aria2.addUri", params, id)
    }

    /**
     * 提交下载任务（单个uri）
     */
    fun addUri(uri: String, options: Options = Options(), position: Int? = null, id: Any? = null) {
        addUri(listOf(uri), options, position, id)
    }

    /**
     * 获取指定任务的状态
     */
    fun tellStatus(gid: String, keys: List<String> = DefaultKeys) {
        call("aria2.tellStatus", buildParams(gid, keys))
    }

    /**
     * 获取当前活动任务
     */
    fun tellActive(keys: List<String> = DefaultKeys) {
        call("aria2.tellActive", buildParams(keys))
    }

    /**
     * 获取版本号
     */
    fun getVersion() {
        call("aria2.getVersion")
    }

    /**
     * 获取会话信息
     */
    fun getSessionInfo() {
        call("aria2.getSessionInfo")
    }

    companion object {
        val DefaultKeys = listOf("gid", "status", "totalLength", "completedLength")
    }
}