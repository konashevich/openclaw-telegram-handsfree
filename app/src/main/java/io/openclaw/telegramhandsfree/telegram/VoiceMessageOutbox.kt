package io.openclaw.telegramhandsfree.telegram

import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

class VoiceMessageOutbox {
    private val queue = ConcurrentLinkedQueue<File>()

    fun enqueue(file: File) {
        queue.add(file)
    }

    fun poll(): File? {
        return queue.poll()
    }

    fun isEmpty(): Boolean = queue.isEmpty()
}
