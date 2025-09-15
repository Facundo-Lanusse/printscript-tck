package adapter

import interpreter.InputProvider


class InputProviderAdapter(
    val provider: InputProvider
): ps.runtime.providers.InputProvider {
    override fun readInput(prompt: String): String {
        return provider.input(prompt)
    }
}
