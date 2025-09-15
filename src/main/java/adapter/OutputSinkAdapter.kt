package adapter

import interpreter.PrintEmitter
import ps.lang.errors.InterpreterException
import ps.runtime.providers.OutputSink

class OutputSinkAdapter(
    val printEmitter: PrintEmitter
) : OutputSink {
    override fun print(message: String) {
        printEmitter.print(message)
    }
}