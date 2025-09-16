package adapter

import interpreter.InputProvider
import interpreter.PrintEmitter

class InputProviderAdapter(
    private val javaProvider: InputProvider,
    private val emitter: PrintEmitter
) : runtime.providers.InputProvider {
    override fun readInput(prompt: String): String {
        emitter.print(prompt)
        return javaProvider.input(prompt)
    }
}