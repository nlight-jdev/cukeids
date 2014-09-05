package com.sony.ngnp.uid.util

import scala.util.matching.Regex

object RegexUtils {
  class RichRegex(underlying: Regex) {
    def matches(s: String) = underlying.pattern.matcher(s).matches
  }
  implicit def regexToRichRegex(r: Regex) = new RichRegex(r)
}
