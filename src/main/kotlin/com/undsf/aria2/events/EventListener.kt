package com.undsf.aria2.events

import com.undsf.aria2.messages.SessionInfo
import com.undsf.aria2.messages.TaskStatus
import com.undsf.aria2.messages.Version

interface EventListener {
    /**
     * 连接成功
     */
    fun onConnected()

    /**
     * 下载任务创建完成
     */
    fun taskCreated(msgId: Any, gid: String)

    /**
     * 任务状态更新
     */
    fun taskStatusUpdated(status: TaskStatus)

    /**
     * 获取到版本信息
     */
    fun onGetVersion(version: Version)

    /**
     * 获取到会话信息
     */
    fun onGetSessionInfo(sessionInfo: SessionInfo)
}