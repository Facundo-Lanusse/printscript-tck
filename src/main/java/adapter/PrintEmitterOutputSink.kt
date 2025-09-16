package adapter

import interpreter.PrintEmitter
import runtime.providers.OutputSink

class PrintEmitterOutputSink(
    private val emitter: PrintEmitter
) : OutputSink {
    override fun print(message: String) {
        emitter.print(message)
    }
}