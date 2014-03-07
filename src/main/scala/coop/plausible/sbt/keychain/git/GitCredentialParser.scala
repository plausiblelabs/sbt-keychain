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
 * Git credential tool output parser.
 *
 * Refer to git-credential(7) man page for details on the credential output format.
 * https://www.kernel.org/pub/software/scm/git/docs/git-credential.html
 */
private[keychain] object GitCredentialParser extends RegexParsers {
   override def skipWhitespace = false
   private val key = """[a-zA-Z]+""".r
   private val value = "=" ~> """.*""".r
   private val sep = "\r\n" | "\n" | "\r" | """\z""".r
   private val keypair = key ~ value ^^ {
     case key ~ value => (key, value)
   }

   private val keypairs: Parser[Map[String, String]] = rep(keypair <~ sep) ^^ (Map() ++ _)

  /**
   * Parse the given Git credential output, returning either a set of key value pairs, or a keychain error.
   *
   * @param input Git credential data.
   * @return Set of key value pairs, or a keychain error.
   */
   def parseInput (input: String): Either[KeychainError, Map[String, String]] = {
     parseAll (keypairs, input) match {
       case Success(v, _) => Right(v)
       case e: NoSuccess => Left(CommandFailed(s"Failed to parse git credential helper output: $e"))
     }
   }
 }
