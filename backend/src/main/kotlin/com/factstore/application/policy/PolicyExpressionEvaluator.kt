package com.factstore.application.policy

import org.springframework.stereotype.Component

/**
 * A condition that cannot be parsed or evaluated (#241). [position] is the 1-based column in the
 * expression (after any `${{ }}` wrapper is removed) where the problem was found.
 */
class ConditionException(message: String, val position: Int) : RuntimeException(message)

/**
 * Evaluates the `if:` conditions of flow templates and YAML policies, for example
 *
 *     ${{ flow.name == "backend-service" }}
 *     exists(flow)
 *     matches(artifact.name, "^backend.*") and not flow.tags["pci-scope"] == "true"
 *
 * Grammar (#241), lowest precedence first:
 *
 *     expr       := orExpr
 *     orExpr     := andExpr ( "or" andExpr )*
 *     andExpr    := notExpr ( "and" notExpr )*
 *     notExpr    := "not" notExpr | primary
 *     primary    := "(" expr ")" | call | comparison
 *     call       := "exists" "(" ("flow" | "artifact") ")"
 *                 | "matches" "(" field "," string ")"
 *     comparison := field ("==" | "!=") string
 *                 | field "in" "[" string ( "," string )* "]"
 *     field      := "flow.name" | "artifact.name" | "artifact.fingerprint"
 *                 | "flow.tags." key | "flow.tags" "[" string "]"
 *
 * Strings are double- or single-quoted; `\` escapes the next character.
 *
 * Anything outside the grammar — a syntax error, an unknown field or function, an invalid
 * regex — throws [ConditionException]. Nothing is silently `false`: callers decide how to fail
 * closed (upload rejects the document, assert treats the attestation as required).
 */
@Component
class PolicyExpressionEvaluator {

    data class EvaluationContext(
        val flowName: String? = null,
        val flowTags: Map<String, String> = emptyMap(),
        val artifactName: String? = null,
        val artifactFingerprint: String? = null
    )

    /** Throws [ConditionException] if [expression] is not a valid condition. */
    fun validate(expression: String) {
        parse(expression)
    }

    /** Evaluates [expression]; throws [ConditionException] if it is not a valid condition. */
    fun evaluate(expression: String, context: EvaluationContext): Boolean =
        parse(expression).eval(context)

    private fun parse(expression: String): Node {
        val expr = expression.trim().let {
            if (it.startsWith("\${{") && it.endsWith("}}")) it.removePrefix("\${{").removeSuffix("}}") else it
        }
        return Parser(Lexer(expr).tokens()).parseAll()
    }

    // ---------------------------------------------------------------- AST

    private sealed interface Node {
        fun eval(ctx: EvaluationContext): Boolean
    }

    private class Or(val left: Node, val right: Node) : Node {
        override fun eval(ctx: EvaluationContext) = left.eval(ctx) || right.eval(ctx)
    }

    private class And(val left: Node, val right: Node) : Node {
        override fun eval(ctx: EvaluationContext) = left.eval(ctx) && right.eval(ctx)
    }

    private class Not(val operand: Node) : Node {
        override fun eval(ctx: EvaluationContext) = !operand.eval(ctx)
    }

    private class Exists(val subject: String) : Node {
        override fun eval(ctx: EvaluationContext) = when (subject) {
            "flow" -> ctx.flowName != null
            else -> ctx.artifactName != null || ctx.artifactFingerprint != null
        }
    }

    private class Matches(val field: Field, val regex: Regex) : Node {
        override fun eval(ctx: EvaluationContext) = field.resolve(ctx)?.let { regex.matches(it) } ?: false
    }

    private class Compare(val field: Field, val negate: Boolean, val value: String) : Node {
        override fun eval(ctx: EvaluationContext) = (field.resolve(ctx) == value) != negate
    }

    private class In(val field: Field, val values: List<String>) : Node {
        override fun eval(ctx: EvaluationContext) = field.resolve(ctx) in values
    }

    private sealed interface Field {
        fun resolve(ctx: EvaluationContext): String?
    }

    private object FlowName : Field {
        override fun resolve(ctx: EvaluationContext) = ctx.flowName
    }

    private object ArtifactName : Field {
        override fun resolve(ctx: EvaluationContext) = ctx.artifactName
    }

    private object ArtifactFingerprint : Field {
        override fun resolve(ctx: EvaluationContext) = ctx.artifactFingerprint
    }

    private class FlowTag(val key: String) : Field {
        override fun resolve(ctx: EvaluationContext) = ctx.flowTags[key]
    }

    // ---------------------------------------------------------------- lexer

    private enum class Kind { IDENT, STRING, LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, EQ, NE, END }

    private data class Token(val kind: Kind, val text: String, val position: Int)

    private class Lexer(private val src: String) {
        private var i = 0

        fun tokens(): List<Token> {
            val out = mutableListOf<Token>()
            while (true) {
                while (i < src.length && src[i].isWhitespace()) i++
                if (i >= src.length) {
                    out += Token(Kind.END, "end of expression", src.length + 1)
                    return out
                }
                val start = i
                val c = src[i]
                out += when {
                    c == '(' -> single(Kind.LPAREN)
                    c == ')' -> single(Kind.RPAREN)
                    c == '[' -> single(Kind.LBRACKET)
                    c == ']' -> single(Kind.RBRACKET)
                    c == ',' -> single(Kind.COMMA)
                    src.startsWith("==", i) -> { i += 2; Token(Kind.EQ, "==", start + 1) }
                    src.startsWith("!=", i) -> { i += 2; Token(Kind.NE, "!=", start + 1) }
                    c == '"' || c == '\'' -> string(c)
                    c.isLetter() || c == '_' -> {
                        while (i < src.length && (src[i].isLetterOrDigit() || src[i] in "_.-")) i++
                        Token(Kind.IDENT, src.substring(start, i), start + 1)
                    }
                    else -> throw ConditionException("unexpected character '$c' at position ${start + 1}", start + 1)
                }
            }
        }

        private fun single(kind: Kind): Token = Token(kind, src[i].toString(), i + 1).also { i++ }

        private fun string(quote: Char): Token {
            val start = i
            i++
            val sb = StringBuilder()
            while (i < src.length && src[i] != quote) {
                if (src[i] == '\\' && i + 1 < src.length) i++
                sb.append(src[i])
                i++
            }
            if (i >= src.length) {
                throw ConditionException("unterminated string starting at position ${start + 1}", start + 1)
            }
            i++
            return Token(Kind.STRING, sb.toString(), start + 1)
        }
    }

    // ---------------------------------------------------------------- parser

    private class Parser(private val tokens: List<Token>) {
        private var p = 0
        private val peek get() = tokens[p]

        fun parseAll(): Node {
            if (peek.kind == Kind.END) throw ConditionException("empty condition", peek.position)
            val node = orExpr()
            if (peek.kind != Kind.END) throw unexpected("end of expression")
            return node
        }

        private fun orExpr(): Node {
            var left = andExpr()
            while (isKeyword("or")) {
                p++
                left = Or(left, andExpr())
            }
            return left
        }

        private fun andExpr(): Node {
            var left = notExpr()
            while (isKeyword("and")) {
                p++
                left = And(left, notExpr())
            }
            return left
        }

        private fun notExpr(): Node {
            if (isKeyword("not")) {
                p++
                return Not(notExpr())
            }
            return primary()
        }

        private fun primary(): Node {
            val tok = peek
            if (tok.kind == Kind.LPAREN) {
                p++
                val inner = orExpr()
                expect(Kind.RPAREN, "')'")
                return inner
            }
            if (tok.kind != Kind.IDENT) throw unexpected("a field, function or '('")
            if (tokens[p + 1].kind == Kind.LPAREN) return call()
            return comparison()
        }

        private fun call(): Node {
            val name = expect(Kind.IDENT, "a function name")
            expect(Kind.LPAREN, "'('")
            return when (name.text) {
                "exists" -> {
                    val subject = expect(Kind.IDENT, "'flow' or 'artifact'")
                    if (subject.text !in setOf("flow", "artifact")) {
                        throw ConditionException(
                            "exists() takes 'flow' or 'artifact', not '${subject.text}' (position ${subject.position})",
                            subject.position
                        )
                    }
                    expect(Kind.RPAREN, "')'")
                    Exists(subject.text)
                }
                "matches" -> {
                    val field = field()
                    expect(Kind.COMMA, "','")
                    val pattern = expect(Kind.STRING, "a quoted regular expression")
                    expect(Kind.RPAREN, "')'")
                    val regex = try {
                        Regex(pattern.text)
                    } catch (e: Exception) {
                        throw ConditionException(
                            "invalid regular expression \"${pattern.text}\" at position ${pattern.position}",
                            pattern.position
                        )
                    }
                    Matches(field, regex)
                }
                else -> throw ConditionException(
                    "unknown function '${name.text}' at position ${name.position}; supported: exists, matches",
                    name.position
                )
            }
        }

        private fun comparison(): Node {
            val field = field()
            val op = peek
            return when {
                op.kind == Kind.EQ || op.kind == Kind.NE -> {
                    p++
                    Compare(field, op.kind == Kind.NE, expect(Kind.STRING, "a quoted value").text)
                }
                isKeyword("in") -> {
                    p++
                    expect(Kind.LBRACKET, "'['")
                    val values = mutableListOf(expect(Kind.STRING, "a quoted value").text)
                    while (peek.kind == Kind.COMMA) {
                        p++
                        values += expect(Kind.STRING, "a quoted value").text
                    }
                    expect(Kind.RBRACKET, "']'")
                    In(field, values)
                }
                else -> throw unexpected("'==', '!=' or 'in'")
            }
        }

        private fun field(): Field {
            val tok = expect(Kind.IDENT, "a field")
            return when {
                tok.text == "flow.name" -> FlowName
                tok.text == "artifact.name" -> ArtifactName
                tok.text == "artifact.fingerprint" -> ArtifactFingerprint
                tok.text.startsWith("flow.tags.") && tok.text.length > "flow.tags.".length ->
                    FlowTag(tok.text.removePrefix("flow.tags."))
                tok.text == "flow.tags" && peek.kind == Kind.LBRACKET -> {
                    p++
                    val key = expect(Kind.STRING, "a quoted tag name")
                    expect(Kind.RBRACKET, "']'")
                    FlowTag(key.text)
                }
                else -> throw ConditionException(
                    "unknown field '${tok.text}' at position ${tok.position}; supported: " +
                        "flow.name, flow.tags.<key>, flow.tags[\"<key>\"], artifact.name, artifact.fingerprint",
                    tok.position
                )
            }
        }

        private fun isKeyword(word: String) = peek.kind == Kind.IDENT && peek.text == word

        private fun expect(kind: Kind, what: String): Token {
            if (peek.kind != kind) throw unexpected(what)
            return tokens[p++]
        }

        private fun unexpected(expected: String): ConditionException {
            val tok = peek
            val found = if (tok.kind == Kind.END) "end of expression" else "'${tok.text}'"
            return ConditionException("expected $expected but found $found at position ${tok.position}", tok.position)
        }
    }
}
