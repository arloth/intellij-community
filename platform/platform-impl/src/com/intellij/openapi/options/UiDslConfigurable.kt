// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus

@Deprecated(message = "Use new UI DSL com.intellij.ui.dsl and UiDslUnnamedConfigurable instead")
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
interface UiDslConfigurable : UnnamedConfigurable {
  fun RowBuilder.createComponentRow()

  abstract class Simple : DslConfigurableBase(), UiDslConfigurable {
    final override fun createPanel(): DialogPanel {
      return panel {
        createComponentRow()
      }
    }

    final override fun isModified() = super.isModified()
    final override fun reset() = super<DslConfigurableBase>.reset()
    final override fun apply() = super.apply()
    final override fun disposeUIResources() = super<DslConfigurableBase>.disposeUIResources()
    final override fun cancel() = super<DslConfigurableBase>.cancel()
  }
}