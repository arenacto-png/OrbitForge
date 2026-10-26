package com.orbitforge.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Lightweight, dependency-free syntax coloring for the in-app editor.
 *
 * It preserves every character and uses identity offset mapping, so caret positions and saved text
 * remain unchanged. Very large files intentionally fall back to plain text to keep typing responsive.
 */
internal class SyntaxHighlightingTransformation private constructor(
    private val extension: String
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        if (source.length > MAX_HIGHLIGHT_CHARS || source.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder()
        var cursor = 0
        for (match in TOKEN_PATTERN.findAll(source)) {
            val start = match.range.first
            val endExclusive = match.range.last + 1
            if (start < cursor) continue

            builder.append(source.substring(cursor, start))
            val token = match.value
            val style = styleFor(token, source, endExclusive)
            if (style == null) {
                builder.append(token)
            } else {
                builder.pushStyle(style)
                builder.append(token)
                builder.pop()
            }
            cursor = endExclusive
        }
        builder.append(source.substring(cursor))
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun styleFor(token: String, source: String, endExclusive: Int): SpanStyle? {
        return when {
            token.startsWith("//") || token.startsWith("/*") ||
                token.startsWith("<!--") || token.startsWith("#") ->
                SpanStyle(color = COMMENT_COLOR, fontStyle = FontStyle.Italic)

            token.startsWith("<") && token.endsWith(">") ->
                SpanStyle(color = TAG_COLOR, fontWeight = FontWeight.Medium)

            token.startsWith("\"") || token.startsWith("'") -> {
                val isJsonKey = extension == "json" &&
                    source.substring(endExclusive).dropWhile { it.isWhitespace() }.startsWith(":")
                SpanStyle(
                    color = if (isJsonKey) PROPERTY_COLOR else STRING_COLOR,
                    fontWeight = if (isJsonKey) FontWeight.Medium else FontWeight.Normal
                )
            }

            NUMBER_PATTERN.matches(token) -> SpanStyle(color = NUMBER_COLOR)
            KEYWORD_PATTERN.matches(token) -> SpanStyle(color = KEYWORD_COLOR, fontWeight = FontWeight.SemiBold)
            else -> null
        }
    }

    companion object {
        private const val MAX_HIGHLIGHT_CHARS = 100_000

        private val COMMENT_COLOR = Color(0xFF8B93A7)
        private val KEYWORD_COLOR = Color(0xFFC792EA)
        private val STRING_COLOR = Color(0xFFC3E88D)
        private val PROPERTY_COLOR = Color(0xFF82AAFF)
        private val NUMBER_COLOR = Color(0xFFF78C6C)
        private val TAG_COLOR = Color(0xFFF07178)

        private val KEYWORD_PATTERN = Regex(
            """(?i:\\b(?:abstract|and|as|assert|async|await|break|case|catch|class|const|continue|data|def|default|delete|do|else|elif|enum|except|export|extends|false|final|finally|for|from|fun|global|if|implements|import|in|instanceof|interface|is|lambda|let|new|nonlocal|not|null|object|open|or|override|package|pass|private|protected|public|raise|return|self|static|super|suspend|switch|this|throw|throws|true|try|type|typeof|undefined|val|var|void|when|while|with|yield)\\b)"""
        )
        private val NUMBER_PATTERN = Regex("""-?\\b\\d+(?:\\.\\d+)?\\b""")
        private val TOKEN_PATTERN = Regex(
            """(?s)//[^\\n]*|/\\*.*?\\*/|<!--.*?-->|#[^\\n]*|</?[A-Za-z][^>]*>|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|(?i:\\b(?:abstract|and|as|assert|async|await|break|case|catch|class|const|continue|data|def|default|delete|do|else|elif|enum|except|export|extends|false|final|finally|for|from|fun|global|if|implements|import|in|instanceof|interface|is|lambda|let|new|nonlocal|not|null|object|open|or|override|package|pass|private|protected|public|raise|return|self|static|super|suspend|switch|this|throw|throws|true|try|type|typeof|undefined|val|var|void|when|while|with|yield)\\b)|-?\\b\\d+(?:\\.\\d+)?\\b"""
        )

        fun forFile(path: String): VisualTransformation {
            val extension = path.substringAfterLast('.', "").lowercase()
            return if (extension in SUPPORTED_EXTENSIONS) {
                SyntaxHighlightingTransformation(extension)
            } else {
                VisualTransformation.None
            }
        }

        private val SUPPORTED_EXTENSIONS = setOf(
            "kt", "kts", "java", "js", "jsx", "ts", "tsx", "json",
            "xml", "html", "css", "py", "yaml", "yml", "sh"
        )
    }
}
