package id.skmnetwork.bukuwarung.printer.escpos

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

object EscPosCommands {
    val ESC: Byte = 0x1B
    val GS: Byte = 0x1D
    val LF: Byte = 0x0A

    // 1. Initialize
    val INIT = byteArrayOf(ESC, 0x40)

    // 2. Alignment
    val ALIGN_LEFT = byteArrayOf(ESC, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(ESC, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(ESC, 0x61, 0x02)

    // 3. Emphasis (Bold)
    val BOLD_ON = byteArrayOf(ESC, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(ESC, 0x45, 0x00)

    // 4. Character Size
    val NORMAL_SIZE = byteArrayOf(ESC, 0x21, 0x00)
    val DOUBLE_HEIGHT = byteArrayOf(ESC, 0x21, 0x10)
    val DOUBLE_WIDTH = byteArrayOf(ESC, 0x21, 0x20)
    val DOUBLE_SIZE = byteArrayOf(ESC, 0x21, 0x30)

    // 5. Underline
    val UNDERLINE_ON = byteArrayOf(ESC, 0x2D, 0x01)
    val UNDERLINE_OFF = byteArrayOf(ESC, 0x2D, 0x00)

    // 6. Paper Cut
    val CUT_FULL = byteArrayOf(GS, 0x56, 0x00)
    val CUT_PARTIAL = byteArrayOf(GS, 0x56, 0x01)

    // 7. Cash Drawer Kick (Pin 2)
    val DRAWER_KICK = byteArrayOf(ESC, 0x70, 0x00, 0x19, 0xFA.toByte())

    fun feedLines(count: Int): ByteArray {
        val safeCount = count.coerceIn(1, 10).toByte()
        return byteArrayOf(ESC, 0x64, safeCount)
    }
}

class EscPosBuilder(
    private val charset: Charset = Charsets.UTF_8
) {
    private val outputStream = ByteArrayOutputStream()

    fun initialize(): EscPosBuilder {
        outputStream.write(EscPosCommands.INIT)
        return this
    }

    fun alignLeft(): EscPosBuilder {
        outputStream.write(EscPosCommands.ALIGN_LEFT)
        return this
    }

    fun alignCenter(): EscPosBuilder {
        outputStream.write(EscPosCommands.ALIGN_CENTER)
        return this
    }

    fun alignRight(): EscPosBuilder {
        outputStream.write(EscPosCommands.ALIGN_RIGHT)
        return this
    }

    fun bold(enable: Boolean): EscPosBuilder {
        outputStream.write(if (enable) EscPosCommands.BOLD_ON else EscPosCommands.BOLD_OFF)
        return this
    }

    fun doubleHeight(enable: Boolean): EscPosBuilder {
        outputStream.write(if (enable) EscPosCommands.DOUBLE_HEIGHT else EscPosCommands.NORMAL_SIZE)
        return this
    }

    fun doubleSize(enable: Boolean): EscPosBuilder {
        outputStream.write(if (enable) EscPosCommands.DOUBLE_SIZE else EscPosCommands.NORMAL_SIZE)
        return this
    }

    fun normalSize(): EscPosBuilder {
        outputStream.write(EscPosCommands.NORMAL_SIZE)
        return this
    }

    fun text(text: String): EscPosBuilder {
        outputStream.write(text.toByteArray(charset))
        return this
    }

    fun textLine(text: String = ""): EscPosBuilder {
        if (text.isNotEmpty()) {
            outputStream.write(text.toByteArray(charset))
        }
        outputStream.write(byteArrayOf(EscPosCommands.LF))
        return this
    }

    fun lineFeed(count: Int = 1): EscPosBuilder {
        outputStream.write(EscPosCommands.feedLines(count))
        return this
    }

    fun cutPaper(partial: Boolean = false): EscPosBuilder {
        outputStream.write(if (partial) EscPosCommands.CUT_PARTIAL else EscPosCommands.CUT_FULL)
        return this
    }

    fun raw(bytes: ByteArray): EscPosBuilder {
        outputStream.write(bytes)
        return this
    }

    fun build(): ByteArray {
        return outputStream.toByteArray()
    }
}
