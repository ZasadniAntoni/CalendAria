package com.github.antonizasadni.calendaria.notesView

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

object MarkdownParser {
    /**
     * A lightweight Markdown to AnnotatedString parser.
     * Supports: # Headers, **Bold**, *Italic*, and - Lists.
     */
    fun parse(text: String): AnnotatedString {
        val baseFontSize = 16
        val baseSp = 16.sp
        return buildAnnotatedString {
            val lines = text.split("\n")
            lines.forEachIndexed { index, line ->
                when {
                    line.startsWith("# ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)) {
                            append(line.removePrefix("# "))
                        }
                    }
                    line.startsWith("## ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                            append(line.removePrefix("## "))
                        }
                    }
                    line.startsWith("### ") -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                            append(line.removePrefix("### "))
                        }
                    }
                    line.startsWith("> ") -> {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = Color.Gray, fontSize = baseSp)) {
                            append("  ┃ ")
                            appendLineWithFormatting(line.removePrefix("> "), baseFontSize)
                        }
                    }
                    line.startsWith("- [ ] ") || line.startsWith("* [ ] ") -> {
                        pushStringAnnotation(tag = "CHECKBOX", annotation = index.toString())
                        withStyle(SpanStyle(fontSize = baseSp)) {
                            append("  ☐ ")
                        }
                        pop()
                        appendLineWithFormatting(line.substring(6), baseFontSize)
                    }
                    line.startsWith("- [x] ") || line.startsWith("* [x] ") || line.startsWith("- [X] ") || line.startsWith("* [X] ") -> {
                        pushStringAnnotation(tag = "CHECKBOX", annotation = index.toString())
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray, fontSize = baseSp)) {
                            append("  ☑ ")
                            appendLineWithFormatting(line.substring(6), baseFontSize)
                        }
                        pop()
                    }
                    line.startsWith("- ") || line.startsWith("* ") -> {
                        withStyle(SpanStyle(fontSize = baseSp)) {
                            append("  • ")
                        }
                        appendLineWithFormatting(line.substring(2), baseFontSize)
                    }
                    line.contains(Regex("^\\d+\\. ")) -> {
                        withStyle(SpanStyle(fontSize = baseSp)) {
                            append("  ")
                        }
                        appendLineWithFormatting(line, baseFontSize)
                    }
                    else -> {
                        appendLineWithFormatting(line, baseFontSize)
                    }
                }
                if (index < lines.size - 1) append("\n")
            }
        }
    }

    private fun AnnotatedString.Builder.appendLineWithFormatting(line: String, baseFontSize: Int) {
        val baseSp = baseFontSize.sp
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        val italicRegex = Regex("\\*(.*?)\\*")
        
        var lastMatchEnd = 0
        
        // Find all matches for bold and italic
        val matches = (boldRegex.findAll(line).map { it to "bold" } + 
                      italicRegex.findAll(line).map { it to "italic" })
                      .sortedBy { it.first.range.first }

        matches.forEach { (match, type) ->
            // Check if we are jumping over a previous match due to overlap
            if (match.range.first < lastMatchEnd) return@forEach

            // Append text before the match
            if (match.range.first > lastMatchEnd) {
                withStyle(SpanStyle(fontSize = baseSp)) {
                    append(line.substring(lastMatchEnd, match.range.first))
                }
            }
            
            // Append the formatted match
            val innerText = match.groupValues[1]
            withStyle(if (type == "bold") SpanStyle(fontWeight = FontWeight.Bold, fontSize = baseSp) 
                      else SpanStyle(fontStyle = FontStyle.Italic, fontSize = baseSp)) {
                append(innerText)
            }
            
            lastMatchEnd = match.range.last + 1
        }
        
        // Append remaining text
        if (lastMatchEnd < line.length) {
            withStyle(SpanStyle(fontSize = baseSp)) {
                append(line.substring(lastMatchEnd))
            }
        }
    }
}
