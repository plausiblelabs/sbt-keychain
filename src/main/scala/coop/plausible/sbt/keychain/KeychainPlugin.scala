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

package coop.plausible.sbt.keychain

import java.nio.charset.StandardCharsets

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import coop.plausible.sbt.keychain.git.{GitConfigOption, GitOptionParser, GitCredentialParser}


/**
 * Implements automatic population of sbt credentials via various keychain sources.
 */
object KeychainPlugin extends sbt.Plugin {
  import sbt._
  import sbt.Keys._

  private val LOG_PREFIX = "[KeychainPlugin]"

  /**
   * Keychain configuration.
   */
  val keychainConfig = config("keychain")

  /** The realm and (optional) username to be used when looking up the account in the keychain. */
  val keychainAccounts = SettingKey[Seq[KeychainAccount]]("User accounts to be fetched from the keychain.")

  /** A task that performs querying of the keychain and returns all available credentials
    * based on the configured publishTo destination */
  val keychainCredentials = TaskKey[Seq[Credentials]]("The keychain credentials.")

  /**
   * Keychain plugin settings.
   */
  lazy val keychainSettings: Seq[Setting[_]] = Seq[Setting[_]](
    keychainAccounts := Seq(),
    keychainCredentials <<= keychainCredentialsTask(keychainAccounts)
  )

  /**
   * Return the credentials to be used for publishing based on the configured publishTo destination.
   *
   * @param accounts The defined accounts.
   * @return The fetched credentials, or an exception if fetching credentials failed.
   */
  private def keychainCredentialsTask (accounts: SettingKey[Seq[KeychainAccount]]) = Def.task {
    /* Find all valid credentials */
    for (
      /* Fetch credentials */
      creds <- {
        /* Iterate over all defined keychain accounts, collecting errors. */
        val results = accounts.value.map { account =>
          getAccountCredentials(account, streams.value.log)
        }

        /* Report errors (side-effecting) */
        accounts.value.view.zip(results).collect {
          case (account, Left(error)) => (account, error)
        }.foreach {
          case (account, FatalKeychainError(msg)) => throw new RuntimeException("$LOG_PREFIX Credential fetch for $account failed: " + msg)
          case (account, AccountNotFound(msg)) => streams.value.log.info(s"$LOG_PREFIX No keychain account found for $account: $msg")
          case (account, other:KeychainError) => streams.value.log.info(s"$LOG_PREFIX Could not fetch keychain credentials for $account: ${other.message}")
        }

        /* Return successful results */
        results.collect {
          case Right(c) => c
        }
      }
    ) yield creds
  }

  /**
   * Look up keychain credentials for the given account and target.
   *
   * @param account A user-declared keychain account.
   * @param logger Task-specific logger.
   * @return Associated credentials, or a keychain error.
   */
  private def getAccountCredentials (account: KeychainAccount, logger: Logger): Either[KeychainError, Credentials] = {
    /* If this is a maven repository, generate a repo URL, and use it to fetch the user's credentials */
    for (
      /* Try parsing the account URL */
      url <- (Try(new URL(account.address)) match {
        case Success(url) => Right(url)
        case Failure(t) => Left(FatalKeychainError("Could not parse URL: " + t))
      }).right;
      creds <- getGitCredentials(url.getProtocol, url.getHost, account.realm, account.username, logger).right
    ) yield creds
  }

    /**
   * Execute the given command, returning either the string output, or a keychain error.
   *
   * @param command The command to execute.
   * @param input Data to pass to the command's standard input, if any.
   * @param logger Task-specific logger.
   */
  private def executeCommand (command: Seq[String], input: Option[Array[Byte]], logger: Logger): Either[KeychainError, String] = {
    import scala.collection.JavaConverters._
    import scala.concurrent.ExecutionContext.Implicits.global

    /*
     * Note: Due to Scala ProcessBuilder's failure to catch exceptions thrown
     * by java.lang.ProcessBuilder.start() (Scala 2.10.3) when it spins up our
     * process on a background thread, we have to use the Java process builder
     * API ourselves.
     *
     * Thanks, unchecked exceptions.
     */

    /* Set up the process builder and launch the process */
    val pb = new java.lang.ProcessBuilder(command.asJava)
    pb.redirectError(ProcessBuilder.Redirect.INHERIT)
    pb.redirectInput(ProcessBuilder.Redirect.PIPE)
    pb.redirectOutput(ProcessBuilder.Redirect.PIPE)

    /* Try launching the process */
    val proc = try {
      pb.start()
    } catch {
      case NonFatal(e) =>
        logger.debug("Command execution failed: " + e)

        /* Non-local early return */
        return Left(CommandFailed(s"Failed to start ${command.mkString(" ")}: ${e.getMessage}"))
    }

    /* Write any data to the process' stdin, asynchronously as to prevent blocking. */
    input.map { inputBytes =>
      scala.concurrent.Future.apply {
        val stdin = proc.getOutputStream
        try {
          stdin.write(inputBytes)
          stdin.close()
        } catch {
          case NonFatal(e) => logger.warn("Failed to write bytes to process' input stream: " + e)
        }
      }
    }

    /* Read process output, if any */
    val output = try {
      val buffer = new Array[Byte](1024)
      val outputBuffer = new StringBuilder
      val stdout = proc.getInputStream

      var read = 0
      do {
        read = stdout.read(buffer)
        if (read > 0) {
          outputBuffer.append(new String(buffer, 0, read))
        }
      } while (read > 0)

      Right(outputBuffer.toString())
    } catch {
      case NonFatal(e) => Left(CommandFailed(s"Failed to read process' ${command.mkString(" ")} stdout stream: $e"))
    }

    /* Wait for process completion */
    val exitCode = proc.waitFor()
    if (exitCode != 0) {
      Left(CommandFailed(s"Process ${command.mkString(" ")} returned a non-zero exit code: $exitCode"))
    } else {
      output
    }
  }

  /**
   * Fetch credentials from git's credential interface.
   *
   * @param scheme The URL scheme (eg, http, https).
   * @param host The destination host.
   * @param realm The authentication realm (eg, HTTP Basic realm).
   * @param user The username if known; otherwise, the git credential interface will attempt to fill in this information.
   * @param logger Task-specific logger.
   * @return Returns credentials, or a string explaining why credentials could not be fetched.
   */
  private def getGitCredentials (scheme: String, host: String, realm: String, user: Option[String], logger: Logger): Either[KeychainError, Credentials] = {
    for (
      /* Fetch the configured git credential helper */
      helper <- executeCommand(Seq("git", "config", "credential.helper"), None, logger).right.map(
        _.trim()
      ).right.flatMap(GitOptionParser.parseInput).right;

      /* Parse the configuration option and determine the target binary */
      helperCommand <- helper match {
        /* Value is a direct shell command */
        // TODO - Pass to sh -c ?
        case GitConfigOption(value, true) => Right(value).right

        /* Value is the name of the helper binary suffix */
        case GitConfigOption(value, false) => Right(s"git-credential-$value").right
      };

      /* Generate the request we'll submit to the helper */
      request <- Right(Seq(
        Some(s"protocol=$scheme"),
        Some(s"host=$host"),
        Some(s"realm=$realm"),
        user.map(s => s"username=$s")
      ).flatten.mkString("\n").getBytes(StandardCharsets.UTF_8)).right;

      /* Execute the git-credential-$helper and parse the results */
      lines <- executeCommand(Seq(helperCommand, "get"), Some(request), logger).right;
      parsed <- GitCredentialParser.parseInput(lines).right;

      /* Extract the credentials */
      username <- parsed.get("username").toRight(AccountNotFound("No username returned in git credential output")).right;
      password <- parsed.get("password").toRight(AccountNotFound("No password returned in git credential output")).right
    ) yield Credentials(realm, host, username, password)
  }

  /**
   * Fetch credentials from the OS X Keychain.
   *
   * @param scheme The URL scheme (eg, http, https).
   * @param host The destination host.
   * @param realm The authentication realm (eg, HTTP Basic realm).
   * @param user The username if known; otherwise, the git credential interface will attempt to fill in this information.
   * @return Returns credentials, or a string explaining why credentials could not be fetched.
   */
  private def getOSXCredentials (scheme: String, host: String, realm: String, user: Option[String]): Either[KeychainError, Credentials] = {
    // TODO:  security find-internet-password -gs $host -r $protocol (SecProtocolType, eg, htps) [-a account name]
    Left(UnsupportedKeychain("The Mac OS X keychain is currently not supported"))
  }

  /** A command execution error result. */
  sealed abstract class KeychainError {
    /** A loggable error message. */
    val message: String
  }

  /** Command execution failed. */
  case class CommandFailed (message:String) extends KeychainError

  /** Fatal error. If returned, execution should be terminated. */
  case class FatalKeychainError (message:String) extends KeychainError

  /** The requested account could not be found */
  case class AccountNotFound (message: String) extends KeychainError

  /** The requested keychain source is not supported */
  case class UnsupportedKeychain (message: String) extends KeychainError
}