package adapter

import java.io.InputStream
import java.io.Reader

class CodePointInputStreamReader(private val input: InputStream) : Reader() {
    override fun read(cbuf: CharArray, off: Int, len: Int): Int {
        var i = 0
        while (i < len) {
            val codePoint = input.read()
            if (codePoint == -1) {
                return if (i == 0) -1 else i
            }
            cbuf[off + i] = codePoint.toChar()
            i++
        }
        return i
    }

    override fun close() {
        input.close()
    }
}
