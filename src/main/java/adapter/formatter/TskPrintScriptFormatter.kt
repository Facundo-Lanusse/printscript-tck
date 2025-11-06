package adapter.formatter

import ConfigLoader
import DocBuilder
import Formatter
import Lexer
import LexerTokenProvider
import RuleGenerator
import TokenStream
import config.BelowLineBraceIfStatementDef
import interpreter.PrintScriptFormatter
import java.io.InputStream
import java.io.Writer

import config.RuleRegistry
import config.ForceRulesInit
import config.RuleIdNameAdapter
import config.FormatterStyleConfig
import config.IndentationDef
import config.InlineBraceIfStatementIdDef
import config.KeywordSpacingAfterDef
import config.LineBreakAfterSemiColonDef
import config.LineBreakBeforePrintlnDef
import config.MaxSpaceBetweenTokensDef
import config.RuleMapping
import config.SpaceAfterColonDef
import config.SpaceAroundAssignmentDef
import config.SpaceAroundOperatorsDef
import config.SpaceBeforeColonDef
import impl.interfaces.Rule


class TskPrintScriptFormatter : PrintScriptFormatter {

    override fun format(
        src: InputStream,
        version: String,
        config: InputStream?,
        writer: Writer
    ) {
        try {
            ForceRulesInit.loadAll() // autoregister

            val reader = src.bufferedReader(Charsets.UTF_8)

            val tokenRule = RuleGenerator.createTokenRule(version)
            val lexer = Lexer(reader, tokenRule)
            val tokenStream: TokenStream = LexerTokenProvider(
                lexer, readSpace = true, readNewline = true
            )

            val json = config?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"

            val adapter = defaultAdapter()
            val loader = ConfigLoader(adapter)
            val style: FormatterStyleConfig = loader.loadFromString(json)


            val rules: List<Rule> = RuleRegistry
                .allRules()
                .filter { r -> style.isActive(r.id) }

            RuleRegistry.allRules().forEach { r ->
                println("RULE ${r.id} default=${r.id.default}")
            }

            Formatter(rules).format(tokenStream, style, DocBuilder.to(writer))
            writer.flush()

        } catch (e: Exception) {
            throw RuntimeException("Formatter failed: ${e.message}", e)
        }
    }

    fun defaultAdapter(): RuleIdNameAdapter =
        RuleIdNameAdapter { name ->
            when (name) {
                "SpaceAroundOperators"     -> RuleMapping(SpaceAroundOperatorsDef) {it}
                "enforce-spacing-around-equals"    -> RuleMapping(SpaceAroundAssignmentDef) { it}
                "enforce-no-spacing-around-equals"    -> RuleMapping(SpaceAroundAssignmentDef) { v ->
                    when (v) { null -> null; is Boolean -> !v; else -> {}}
                }
                "Indentation"              -> RuleMapping(IndentationDef) {it}
                "KeywordSpacingAfter"      -> RuleMapping(KeywordSpacingAfterDef) {it}
                "line-breaks-after-println" -> RuleMapping(LineBreakBeforePrintlnDef) {it}
                "mandatory-space-surrounding-operations" -> RuleMapping(SpaceAroundOperatorsDef) {it}
                "enforce-spacing-before-colon-in-declaration" -> RuleMapping(SpaceBeforeColonDef) {it}
                "enforce-spacing-after-colon-in-declaration" -> RuleMapping(SpaceAfterColonDef) {it}
                "if-brace-below-line" -> RuleMapping(BelowLineBraceIfStatementDef) {it}
                "if-brace-same-line" -> RuleMapping(InlineBraceIfStatementIdDef) {it}
                "indent-inside-if" -> RuleMapping(IndentationDef) {it}
                "mandatory-line-break-after-statement" -> RuleMapping(LineBreakAfterSemiColonDef) {it}
                "mandatory-single-space-separation" -> RuleMapping(MaxSpaceBetweenTokensDef) {it}
                else -> null
            }
        }
}