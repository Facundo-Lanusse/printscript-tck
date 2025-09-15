package adapter

import Lexer
import LexerTokenProvider
import interpreter.ErrorHandler
import interpreter.InputProvider
import interpreter.PrintEmitter
import interpreter.PrintScriptInterpreter
import parser.Parser
import ps.lang.errors.InterpreterException
import ps.runtime.core.InterpreterRuntimeFactory
import validators.provider.DefaultValidatorsProvider
import java.io.InputStream


class InterpreterAdapter : PrintScriptInterpreter {
    override fun execute(
        src: InputStream?,
        version: String?,
        emitter: PrintEmitter?,
        handler: ErrorHandler?,
        provider: InputProvider?
    ) {
        if (src == null || version == null || emitter == null || handler == null || provider == null) {
            throw InterpreterException("invalid parameters")
        }
        CodePointInputStreamReader(src).use { reader ->
            val tokenRule = RuleGenerator.createTokenRule(version)
            val lexer = Lexer(reader, tokenRule)
            val tokenProvider = LexerTokenProvider(lexer)
            val validatorsProvider = DefaultValidatorsProvider()
            val parser = Parser(validatorsProvider)
            val inputProvider = InputProviderAdapter(provider)
            val outputSink = OutputSinkAdapter(emitter)
            val interpreter = InterpreterRuntimeFactory().createRuntime(
                outputSink = outputSink,
                inputProvider = inputProvider
            )

            while (tokenProvider.peek().type !is TokenType.EOF) {
                val parseResult = parser.parse(tokenProvider)
                when (parseResult) {
                    is Result.Success -> {
                        val executionResult = interpreter.execute(parseResult.value)
                        if (executionResult is Result.Failure) {
                            throw executionResult.error
                        }
                    }
                    is Result.Failure -> {
                        throw Exception(parseResult.error.message ?: "Unknown parse error")
                    }
                }
            }
        }
    }
}