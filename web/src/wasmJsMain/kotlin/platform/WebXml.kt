// SPDX-License-Identifier: AGPL-3.0-only
package me.him188.ani.utils.xml

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parseSource
import com.fleeksoft.ksoup.parser.Parser
import com.fleeksoft.ksoup.select.QueryParser as KsoupQueryParser
import kotlinx.io.Source

actual object Xml {
    actual fun parse(string: String): Document = parse(string, "")
    actual fun parse(string: String, baseUrl: String): Document = Ksoup.parse(string, baseUri = baseUrl, parser = Parser.xmlParser())
    actual fun parse(source: Source): Document = parse(source, "")
    actual fun parse(source: Source, baseUrl: String): Document = Ksoup.parseSource(source, baseUri = baseUrl, parser = Parser.xmlParser())
}
actual object Html {
    actual fun parse(string: String): Document = parse(string, "")
    actual fun parse(string: String, baseUrl: String): Document = Ksoup.parse(string, baseUri = baseUrl, parser = Parser.htmlParser())
    actual fun parse(source: Source): Document = parse(source, "")
    actual fun parse(source: Source, baseUrl: String): Document = Ksoup.parseSource(source, baseUri = baseUrl, parser = Parser.htmlParser())
}
actual object QueryParser {
    actual fun parseSelector(selector: String): Evaluator = KsoupQueryParser.parse(selector)
}
