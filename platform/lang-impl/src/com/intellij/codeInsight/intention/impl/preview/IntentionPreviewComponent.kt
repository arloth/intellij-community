// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.preview

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel

internal class IntentionPreviewComponent(project: Project) : JBLoadingPanel(BorderLayout(),
                                                                            { panel -> IntentionPreviewLoadingDecorator(panel, project) }) {
  private var NO_PREVIEW_LABEL = JLabel(CodeInsightBundle.message("intention.preview.no.available.text") + "     ").also { setupLabel(it) }
  private var LOADING_LABEL = JLabel(CodeInsightBundle.message("intention.preview.loading.preview") + "     ").also { setupLabel(it) }

  var editors: List<EditorEx> = emptyList()
  val htmlContent = JEditorPane().also { pane -> pane.editorKit = JBHtmlEditorKit() }

  val multiPanel: MultiPanel = object : MultiPanel() {
    override fun create(key: Int): JComponent {
      return when (key) {
        NO_PREVIEW -> NO_PREVIEW_LABEL
        LOADING_PREVIEW -> LOADING_LABEL
        HTML_PREVIEW -> htmlContent
        else -> {
          if (editors.isEmpty()) return NO_PREVIEW_LABEL

          IntentionPreviewEditorsPanel(mutableListOf<EditorEx>().apply { addAll<EditorEx>(editors) })
        }
      }
    }
  }

  init {
    add(multiPanel)
    setLoadingText(CodeInsightBundle.message("intention.preview.loading.preview"))
  }

  companion object {
    const val NO_PREVIEW = -1
    const val LOADING_PREVIEW = -2
    const val HTML_PREVIEW = -3

    private fun setupLabel(label: JLabel) {
      label.border = JBUI.Borders.empty(3)
      label.background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    }
  }
}