package com.undsf.aria2.messages

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class TaskStatus(
    var gid: String,
    var status: StatusValue? = null,
    var totalLength: Long? = null,
    var completedLength: Long? = null,
) {
    enum class StatusValue {
        active,
        waiting,
        paused,
        error,
        complete,
        removed
    }

    override fun toString(): String {
        val builder = StringBuilder(gid)
        if (status != null) {
            builder.append(" ${status!!.name}")
        }
        if (completedLength != null && totalLength != null) {
            builder.append(" ${completedLength}/${totalLength}")
        }
        else if (completedLength != null && totalLength == null) {
            builder.append(" ${completedLength} loaded.")
        }
        else if (completedLength == null && totalLength != null) {
            builder.append(" unknown/$totalLength")
        }
        return builder.toString()
    }
}