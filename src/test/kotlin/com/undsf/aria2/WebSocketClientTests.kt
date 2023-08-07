package com.undsf.aria2

import com.undsf.aria2.events.EventListener
import com.undsf.aria2.messages.Options
import com.undsf.aria2.messages.SessionInfo
import com.undsf.aria2.messages.TaskStatus
import com.undsf.aria2.messages.Version
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

private val logger = LoggerFactory.getLogger(WebSocketClientTests::class.java)

class WebSocketClientTests {
    @Test
    @Order(1)
    fun testGetVersion() {
        client.getVersion()
        logger.info("已发起获取Aria2版本请求")
        TimeUnit.SECONDS.sleep(2)
    }

    @Test
    @Order(2)
    fun testGetSessionInfo() {
        client.getSessionInfo()
        logger.info("已发起获取Aria2会话请求")
        TimeUnit.SECONDS.sleep(2)
    }

    @Test
    @Order(3)
    fun testAddUri() {
        client.addUri(
            "https://i7.nhentai.net/galleries/28536/12.gif",
            options = Options(
                dir = "D:\\Temp\\aria2client\\9907",
                out = "12.gif",
                proxy = "http://127.0.0.1:8118"
            ),
            id = "NH9907_P12"
        )
        TimeUnit.SECONDS.sleep(10)
    }

    companion object {
        var client = WebSocketClient(
            secret = "47bfbcf3",
            listener = object : EventListener {
                override fun onConnected() {
                    logger.info("已连接到Aria2服务端")
                }

                override fun taskCreated(msgId: Any, gid: String) {
                    logger.info("任务创建完成：${msgId} -> ${gid}")
                }

                override fun taskStatusUpdated(status: TaskStatus) {
                    logger.info("任务状态更新：${status}")
                }

                override fun onGetVersion(version: Version) {
                    logger.info("获取到Aria2版本号：${version.version}")
                }

                override fun onGetSessionInfo(sessionInfo: SessionInfo) {
                    logger.info("获取到Aria2会话信息：${sessionInfo.sessionId}")
                }
            }
        )

        @BeforeAll
        @JvmStatic
        fun testConnect() {
            client.connect()
            logger.info("首次检查是否已连接：${client.connected}")
            TimeUnit.SECONDS.sleep(1)
            logger.info("再次检查是否已连接：${client.connected}")
            Assertions.assertTrue(client.connected)
        }
    }
}