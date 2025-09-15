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

class InterpreterAdapter: PrintScriptInterpreter {
    /**
     * executes a PrintScript file handling its resulting messages and errors.
     * @param src Source file.
     * @param version PrintScript version, 1.0 and 1.1 must be supported.
     * @param emitter interface where print statements must be called.
     * @param handler interface where all syntax and semantic error will be reported.
     * @param provider interface that provides input values during the execution.
     */
    override fun execute(
        src: InputStream?,
        version: String?,
        emitter: PrintEmitter?,
        handler: ErrorHandler?,
        provider: InputProvider?
    ) {
        if (src == null || version == null || emitter == null || handler == null || provider == null ) {
            throw InterpreterException("invalid parameters")
        }

        // Usamos un BufferedReader para una lectura eficiente, línea por línea o por bloques.
        src.bufferedReader().use { reader ->
            // 1. Configuración de los componentes
            val tokenRule = RuleGenerator.createTokenRule(version)
            val lexer = Lexer(reader, tokenRule)
            val tokenProvider = LexerTokenProvider(lexer)
            val validatorsProvider = DefaultValidatorsProvider() // Podría ser versionado en el futuro
            val parser = Parser(validatorsProvider)
            val inputProvider = InputProviderAdapter(provider)
            val outputSink = OutputSinkAdapter(emitter)
            val interpreter = InterpreterRuntimeFactory().createRuntime(
                outputSink = outputSink,
                inputProvider = inputProvider
            )

            // 2. Bucle de procesamiento (la clave para archivos grandes)
            while (tokenProvider.peek().type !is TokenType.EOF) {

                // Parseamos la siguiente sentencia para obtener un AST Node
                val parseResult = parser.parse(tokenProvider)

                when (parseResult) {
                    is Result.Success -> {
                        // Si el parseo fue exitoso, ejecutamos el nodo del AST
                        val executionResult = interpreter.execute(parseResult.value)
                        if (executionResult is Result.Failure) {
                            // Si la ejecución falla, lanzamos una excepción para detener el proceso.
                            throw executionResult.error
                        }
                    }
                    is Result.Failure -> {
                        // Si el parseo falla, lanzamos una excepción con el error.
                        throw Exception(parseResult.error.message ?: "Error de parseo desconocido")
                    }
                }
            }
        }
    }

}