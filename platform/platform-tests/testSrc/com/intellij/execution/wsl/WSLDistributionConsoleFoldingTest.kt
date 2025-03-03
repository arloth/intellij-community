// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLDistribution.*
import com.intellij.openapi.util.NullableLazyValue
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import junit.framework.TestCase.assertEquals
import org.junit.AfterClass
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test

class WSLDistributionConsoleFoldingTest : BareTestFixtureTestCase() {
   private val folding = WslDistributionConsoleFolding()

  private val wsl: WSLDistribution
    get() {
      val value = WSL?.value
      Assume.assumeTrue("No WSL distributions available", value != null)
      return value!!
    }

  companion object {
    private var WSL: NullableLazyValue<WSLDistribution?>? = NullableLazyValue.createValue {
      val distributions = WslDistributionManager.getInstance().installedDistributions
      if (distributions.isEmpty()) return@createValue null
      val distribution = distributions[0]
      if (distribution is WSLDistributionLegacy || !IoTestUtil.reanimateWslDistribution(distribution.id)) return@createValue null
      distribution
    }

    @BeforeClass
    @JvmStatic
    fun checkEnvironment() {
      IoTestUtil.assumeWindows()
      IoTestUtil.assumeWslPresence()
    }

    @AfterClass
    @JvmStatic
    fun afterClass() {
      WSL = null
    }
  }

  @Test
  fun `should fold`() {
    fun assertShouldFold(commandLine: GeneralCommandLine = GeneralCommandLine("echo"),
                         options: WSLCommandLineOptions) {
      val line = wsl.patchCommandLine(commandLine, null, options).commandLineString
      assertShouldFold(expected = true, line = line)
    }

    val defaultOptions = WSLCommandLineOptions()

    assertShouldFold(options = defaultOptions)
    assertShouldFold(commandLine = GeneralCommandLine("foo", "bar", "buz"), options = defaultOptions)
    assertShouldFold(options = WSLCommandLineOptions().setExecuteCommandInDefaultShell(!defaultOptions.isExecuteCommandInDefaultShell))
    assertShouldFold(options = WSLCommandLineOptions().setExecuteCommandInShell(!defaultOptions.isExecuteCommandInShell))
    assertShouldFold(options = WSLCommandLineOptions().setExecuteCommandInInteractiveShell(!defaultOptions.isExecuteCommandInInteractiveShell))
    assertShouldFold(options = WSLCommandLineOptions().setExecuteCommandInLoginShell(!defaultOptions.isExecuteCommandInLoginShell))
    assertShouldFold(options = WSLCommandLineOptions().setExecuteCommandInLoginShell(!defaultOptions.isExecuteCommandInLoginShell))
    assertShouldFold(options = WSLCommandLineOptions().setSudo(!defaultOptions.isSudo))
    assertShouldFold(options = WSLCommandLineOptions().setRemoteWorkingDirectory("/foo/bar/buz"))
    assertShouldFold(options = WSLCommandLineOptions().setRemoteWorkingDirectory("/foo bar/buz buz buz"))
    assertShouldFold(commandLine = GeneralCommandLine("echo").withEnvironment("foo", "bar"), options = WSLCommandLineOptions().setPassEnvVarsUsingInterop(true))
    assertShouldFold(commandLine = GeneralCommandLine("echo").withEnvironment("foo", "bar"), options = WSLCommandLineOptions())
    assertShouldFold(options = WSLCommandLineOptions().addInitCommand("foo bar"))
  }

  @Test
  fun `should not fold`() {
    fun assertShouldNotFold(line: String) {
      assertShouldFold(expected = false, line = line)
    }

    assertShouldNotFold("Foo bar")
    assertShouldNotFold("abracadabra")
    assertShouldNotFold("wsl.exe") // random wsl.exe
    assertShouldNotFold("wsl.exe --exec echo") // no --distribution
    assertShouldNotFold("--distribution Ubuntu-18.04 --exec echo") // no wsl.exe
    assertShouldNotFold("--distribution Ubuntu-18.04 wsl.exe --exec echo") // --distribution before wsl.exe
    assertShouldNotFold("wsl.exe --distribution Ubuntu-18.04") // no --exec

    val wslEcho = wsl.patchCommandLine(GeneralCommandLine("echo"), null, WSLCommandLineOptions()).commandLineString
    val wslEchoDefaultShell = wsl.patchCommandLine(GeneralCommandLine("echo"), null,
      WSLCommandLineOptions().setExecuteCommandInDefaultShell(true)).commandLineString

    assertShouldNotFold(wslEcho.remove(WSL_EXE)) // no wsl.exe
    assertShouldNotFold(wslEcho.remove(DISTRIBUTION_PARAMETER)) // no --distribution
    assertShouldNotFold(DISTRIBUTION_PARAMETER + wslEcho.remove(DISTRIBUTION_PARAMETER)) // --distribution before wsl.exe
    assertShouldNotFold(wslEcho.remove(EXEC_PARAMETER)) // no --exec
    assertShouldNotFold(wslEchoDefaultShell.remove(SHELL_PARAMETER)) // no $SHELL -c
    assertShouldNotFold(EXEC_PARAMETER + wslEcho.remove(EXEC_PARAMETER)) // --exec before wsl.exe
    assertShouldNotFold("&& " + wslEcho.remove(EXEC_PARAMETER)) // && before wsl.exe

    val wslExeIndex = wslEcho.indexOf(WSL_EXE)
    val system32Prefix = wslEcho.substring(0, wslExeIndex)
    assertShouldNotFold(wslEcho.remove(system32Prefix)) // no C:\WINDOWS\system32
    assertShouldNotFold(wslEcho.replace(system32Prefix, "foo bar")) // C:\WINDOWS\system32 broken
  }

  private fun assertShouldFold(expected: Boolean, line: String) {
    assertEquals(
      "Should ${if (expected) "fold" else "NOT fold"} line: $line",
      expected,
      folding.shouldFoldLineNoProject(line)
    )
  }

  @Test
  fun `replacement text`() {
    fun assertReplacement(commandLine: GeneralCommandLine = GeneralCommandLine("echo"), options: WSLCommandLineOptions) {
      val commandLineString = commandLine.commandLineString
      val expectedLine = if (options.isExecuteCommandInShell &&
                             !options.isExecuteCommandInDefaultShell &&
                             options.remoteWorkingDirectory.isNullOrEmpty() &&
                             (options.isPassEnvVarsUsingInterop || commandLine.environment.isEmpty()) &&
                             options.initShellCommands.isEmpty()) {
        if (commandLineString.contains(" ")) {
          "${options.shellPath} -c \"$commandLineString\""
        }
        else {
          "${options.shellPath} -c $commandLineString"
        }
      } else {
        commandLineString
      }
      val expected = ExecutionBundle.message("wsl.folding.placeholder", wsl.msId, expectedLine)
      val actual = folding.getPlaceholderText(wsl.patchCommandLine(commandLine, null, options).commandLineString)
      assertEquals(expected, actual)
    }

    val defaultOptions = WSLCommandLineOptions()

    assertReplacement(options = defaultOptions)
    assertReplacement(commandLine = GeneralCommandLine("foo", "-bar", "/baz"), options = defaultOptions)
    assertReplacement(options = WSLCommandLineOptions().setExecuteCommandInDefaultShell(!defaultOptions.isExecuteCommandInDefaultShell))
    assertReplacement(options = WSLCommandLineOptions().setExecuteCommandInShell(!defaultOptions.isExecuteCommandInShell))
    assertReplacement(options = WSLCommandLineOptions().setSudo(!defaultOptions.isSudo))
    assertReplacement(options = WSLCommandLineOptions().setRemoteWorkingDirectory("/foo/bar/buz"))
    assertReplacement(options = WSLCommandLineOptions().setRemoteWorkingDirectory("/foo bar/buz buz buz"))
    assertReplacement(commandLine = GeneralCommandLine("echo").withEnvironment("foo", "bar"), options = WSLCommandLineOptions().setPassEnvVarsUsingInterop(true))
    assertReplacement(commandLine = GeneralCommandLine("echo").withEnvironment("foo", "bar"), options = WSLCommandLineOptions())
    assertReplacement(options = WSLCommandLineOptions().addInitCommand("foo bar"))


  }
}

private fun String.remove(substring: String): String {
  val start = this.indexOf(substring)
  if (start < 0) {
    return this
  }

  return this.removeRange(start, start + substring.length)
}