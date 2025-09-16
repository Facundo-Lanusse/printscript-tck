package adapter

import interpreter.PrintEmitter
import ps.runtime.providers.ConsoleOutputSink
import ps.runtime.providers.OutputSink

class PrintEmitterOutputSink(
    private val emitter: PrintEmitter
) : OutputSink {
    override fun print(message: String) {
        emitter.print(message)
    }
}