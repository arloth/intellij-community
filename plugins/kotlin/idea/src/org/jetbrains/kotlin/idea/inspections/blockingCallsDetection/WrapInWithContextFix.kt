// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.inspections.blockingCallsDetection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.core.replaced
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

internal class WrapInWithContextFix : LocalQuickFix {
    override fun getFamilyName(): String {
        return KotlinBundle.message("intention.wrap.in.with.context")
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val callExpression = descriptor.psiElement.parentOfType<KtCallExpression>() ?: return
        val ktPsiFactory = KtPsiFactory(project)
        val wrappedBlockingCall = callExpression.replaced(
            ktPsiFactory.createExpression(
                """
                    |kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    |   ${callExpression.text}
                    |}
                """.trimMargin()
            )
        )
        CoroutineBlockingCallInspectionUtils.postProcessQuickFix(wrappedBlockingCall, project)
    }
}