/*
 * Copyright (c) 2013-2014 Plausible Labs Cooperative, Inc.
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package coop.plausible.sbt.keychain.git

import scala.util.parsing.combinator.RegexParsers
import coop.plausible.sbt.keychain.KeychainPlugin._

/**
 * Parse git configuration option values.
 */
private[keychain] object GitOptionParser extends RegexParsers {
  override def skipWhitespace = true

  private val isShellCommand = """!""".r
  private val quotedString = """(["'])(?:\\?.)*?\1""".r ^^ (str => str.slice(1, str.length-1))
  private val unquotedValue = """.*""".r

  private val option = opt(isShellCommand) ~ (quotedString | unquotedValue) ^^ {
    case Some(_) ~ cmd => GitConfigOption(cmd, true)
    case None ~ cmd => GitConfigOption(cmd, false)
  }

  /**
   * Parse the given Git credential output, returning either a set of key value pairs, or a keychain error.
   *
   * @param input Git credential data.
   * @return Set of key value pairs, or a keychain error.
   */
  def parseInput (input: String): Either[KeychainError, GitConfigOption] = {
    parse (option, input) match {
      case Success(v, _) => Right(v)
      case e: NoSuccess => Left(CommandFailed(s"Failed to parse git config option: $e"))
    }
  }
}
