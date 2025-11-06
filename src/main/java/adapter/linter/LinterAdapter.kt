package adapter.linter

import Lexer
import LexerTokenProvider
import interpreter.ErrorHandler
import interpreter.PrintScriptLinter
import parser.Parser
import validators.provider.DefaultValidatorsProvider
import java.io.InputStream
import Analyzer
import AnalyzerConfig
import Report
import AstNode
import language.errors.InterpreterException
import naming.IdentifierNamingRule
import naming.IdentifierNamingConfig
import naming.IdentifierCase
import shared.AnalyzerRule
import shared.RuleConfig
import shared.RuleDefinition
import simple.SimpleArgRule
import simple.SimpleArgConfig
import simple.SimpleArgDef
import utils.RuleOwner
import utils.Type
import java.io.InputStreamReader
import kotlin.io.path.createTempFile

class LinterAdapter : PrintScriptLinter {
    override fun lint(src: InputStream?, version: String?, config: InputStream?, handler: ErrorHandler?) {
        if (src == null || version == null || config == null || handler == null) {
            throw InterpreterException("invalid parameters")
        }
        try {
            val analyzerConfig = createAnalyzerConfigFromInputStream(config)
            val rules = createRulesForTckIds()
            val analyzer = Analyzer(rules)

            var finalReport = Report.inMemory()
            val inputReader = InputStreamReader(src, "UTF-8")

            val tokenRule = RuleGenerator.createTokenRule(version)
            val lexer = Lexer(inputReader, tokenRule)
            val tokenProvider = LexerTokenProvider(lexer, readSpace = false, readNewline = false)
            val validatorsProvider = DefaultValidatorsProvider(version)
            val parser = Parser(validatorsProvider)

            val astNodes = mutableListOf<AstNode>()
            while (tokenProvider.peek().type !is TokenType.EOF) {
                when (val parseResult = parser.parse(tokenProvider)) {
                    is Result.Success -> astNodes.add(parseResult.value)
                    is Result.Failure -> {
                        handler.reportError(parseResult.error.message ?: "Parse error")
                        return
                    }
                }
            }

            for (astNode in astNodes) {
                finalReport = analyzer.analyze(astNode, finalReport, analyzerConfig)
            }

            if (finalReport.size() > 0) {
                val firstDiagnostic = finalReport.first()
                handler.reportError("${firstDiagnostic.ruleId}: ${firstDiagnostic.message}")
            }
        } catch (e: Exception) {
            handler.reportError("Linting error: ${e.message}")
        }
    }

    private fun createAnalyzerConfigFromInputStream(config: InputStream): AnalyzerConfig {
        val configText = config.bufferedReader().use { it.readText() }
        val tempFile = createTempFile("linter-config-", ".json").toFile()
        tempFile.writeText(configText)

        val tckDefs = createTckRuleDefinitions()
        return AnalyzerConfig.fromPath(tempFile.path, tckDefs)
    }

    private fun createTckRuleDefinitions(): List<RuleDefinition<RuleConfig>> =
        listOf(
            // identifier_format
            object : RuleDefinition<IdentifierNamingConfig> {
                override val id: String = "identifier_format"
                override val description = "Identifiers must follow the configured naming style"
                override val default: IdentifierNamingConfig = IdentifierNamingConfig(IdentifierCase.CAMEL_CASE, false)
                override val owner: RuleOwner = RuleOwner.USER
                override val type: Type = Type.WARNING

                override fun parse(configMap: Map<String, String>): IdentifierNamingConfig {
                    val input: String? = configMap["identifier_format"]
                    return when (input?.trim()?.lowercase()) {
                        "camel case" -> IdentifierNamingConfig(IdentifierCase.CAMEL_CASE, true)
                        "snake case" -> IdentifierNamingConfig(IdentifierCase.SNAKE_CASE, true)
                        else -> IdentifierNamingConfig(IdentifierCase.CAMEL_CASE, false)
                    }
                }
            },

            // mandatory-variable-or-literal-in-println
            object : SimpleArgDef {
                override val id: String = "mandatory-variable-or-literal-in-println"
                override val description = "Println must have variable or literal"
                override val default: SimpleArgConfig = SimpleArgConfig(false)
                override val owner: RuleOwner = RuleOwner.USER
                override val restrictedCases: Set<String> = setOf("println")
                override val type: Type = Type.WARNING

                override fun parse(configMap: Map<String, String>): SimpleArgConfig {
                    val enabled = configMap[id]?.toBoolean() ?: false
                    return SimpleArgConfig(enabled)
                }
            },

            // mandatory-variable-or-literal-in-readInput
            object : SimpleArgDef {
                override val id: String = "mandatory-variable-or-literal-in-readInput"
                override val description = "ReadInput must have variable or literal"
                override val default: SimpleArgConfig = SimpleArgConfig(false)
                override val owner: RuleOwner = RuleOwner.USER
                override val restrictedCases: Set<String> = setOf("readInput")
                override val type: Type = Type.WARNING

                override fun parse(configMap: Map<String, String>): SimpleArgConfig {
                    val enabled = configMap[id]?.toBoolean() ?: false
                    return SimpleArgConfig(enabled)
                }
            },
        )

    private fun createRulesForTckIds(): List<AnalyzerRule<out RuleConfig>> {
        val defs = createTckRuleDefinitions()
        return listOf(
            @Suppress("UNCHECKED_CAST")
            IdentifierNamingRule(defs[0] as RuleDefinition<IdentifierNamingConfig>),
            SimpleArgRule(defs[1] as SimpleArgDef),
            SimpleArgRule(defs[2] as SimpleArgDef),
        )
    }
}