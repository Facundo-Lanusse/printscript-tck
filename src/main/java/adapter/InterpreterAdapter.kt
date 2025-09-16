package adapter

import AstNode
import Lexer
import LexerTokenProvider
import Result
import RuleGenerator
import interpreter.ErrorHandler
import interpreter.InputProvider
import interpreter.PrintEmitter
import interpreter.PrintScriptInterpreter
import parser.Parser
import ps.lang.errors.InterpreterException
import ps.lang.errors.NoStatementExecutorError
import ps.runtime.core.InterpreterRuntimeFactory
import ps.runtime.providers.SystemEnvProvider
import validators.provider.DefaultValidatorsProvider
import java.io.InputStream
import java.io.InputStreamReader

class PrintScriptInterpreterAdapter : PrintScriptInterpreter {

    override fun execute(
        src: InputStream,
        version: String,
        emitter: PrintEmitter,
        handler: ErrorHandler,
        provider: InputProvider
    ) {
        try {
            // Adapters
            val outputSink = PrintEmitterOutputSink(emitter)
            val inputProvider = InputProviderAdapter(provider, emitter)

            // Runtime
            val runtimeFactory = InterpreterRuntimeFactory()
            val interpreter = runtimeFactory.createRuntime(
                inputProvider = inputProvider,
                outputSink = outputSink,
            )

            // Lexer
            val reader = InputStreamReader(src, Charsets.UTF_8)
            val tokenRule = RuleGenerator.createTokenRule(version)
            val lexer = Lexer(reader, tokenRule)
            val tokenProvider = LexerTokenProvider(lexer, false)

            // Parser
            val validatorsProvider = getValidatorsProviderForVersion(version)
            val parser = Parser(validatorsProvider)

            // Parse & execute
            while (true) {
                when (val parseResult = parser.parse(tokenProvider)) {
                    is Result.Success -> {
                        val astNode: AstNode = parseResult.value
                        when (val exec = interpreter.execute(astNode)) {
                            is Result.Success -> {
                                // OK, seguimos con el próximo statement
                            }
                            is Result.Failure -> {
                                handler.reportError("Runtime error: ${exec.error.message}")
                                break
                            }
                        }
                    }

                    is Result.Failure -> {
                        val msg = parseResult.errorOrNull().toString().trim()
                        if (isPureEof(msg)) {
                            // Fin de input sin error
                            break
                        } else {
                            handler.reportError("Parse error: $msg")
                            break
                        }
                    }
                }
            }
        } catch (e: NoStatementExecutorError) {
            handler.reportError("No statement executor: ${e.message}")
        } catch (e: InterpreterException) {
            handler.reportError("Interpreter error: ${e.message}")
        } catch (e: Exception) {
            handler.reportError("Unexpected error: ${e.message}")
        }
    }

    private fun getValidatorsProviderForVersion(version: String): DefaultValidatorsProvider =
        when (version) {
            "1.0", "1.1" -> DefaultValidatorsProvider()
            else -> throw IllegalArgumentException("Unsupported PrintScript version: $version")
        }

    /**
     * Detecta fin de archivo "puro" sin error semántico/sintáctico.
     * Ajustá los patrones si tu parser usa otro texto.
     */
    private fun isPureEof(msg: String): Boolean {
        if (msg.isBlank()) return false

        val equalsEof =
            msg.equals("EOF", true) ||
                    msg.equals("end of file", true) ||
                    msg.equals("unexpected end of input", true)

        // Parser devolviendo NoValidParser con un único token EOF
        val noValidParserOnlyEof =
            msg.contains("NoValidParser") &&
                    msg.contains("Token(type=EOF")

        return equalsEof || noValidParserOnlyEof
    }
}
