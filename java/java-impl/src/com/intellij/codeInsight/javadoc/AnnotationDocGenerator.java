// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.javadoc;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public final class AnnotationDocGenerator {
  private static final Logger LOG = Logger.getInstance(AnnotationDocGenerator.class);
  @NotNull private final PsiAnnotation myAnnotation;
  @NotNull private final PsiJavaCodeReferenceElement myNameReference;
  @NotNull private final PsiElement myContext;
  @Nullable private final PsiClass myTargetClass;
  private final boolean myResolveNotPossible;

  private AnnotationDocGenerator(@NotNull PsiAnnotation annotation,
                                 @NotNull PsiJavaCodeReferenceElement nameReference,
                                 @NotNull PsiElement context) {
    myAnnotation = annotation;
    myNameReference = nameReference;
    myContext = context;

    boolean indexNotReady = false;
    PsiElement target = null;
    try {
      target = nameReference.resolve();
    }
    catch (IndexNotReadyException e) {
      LOG.debug(e);
      indexNotReady = true;
    }
    myTargetClass = ObjectUtils.tryCast(target, PsiClass.class);
    myResolveNotPossible = indexNotReady;
  }

  boolean isNonDocumentedAnnotation() {
    return myTargetClass != null
           ? !JavaDocInfoGenerator.isDocumentedAnnotationType(myTargetClass)
           : isKnownNonDocumented(myAnnotation.getQualifiedName());
  }

  private static boolean isKnownNonDocumented(String annoQName) {
    return Flow.class.getName().equals(annoQName);
  }

  boolean isExternal() {
    return AnnotationUtil.isExternalAnnotation(myAnnotation);
  }

  public String getAnnotationQualifiedName() {
    return myAnnotation.getQualifiedName();
  }

  public boolean isInferred() {
    return AnnotationUtil.isInferredAnnotation(myAnnotation);
  }

  private static void appendStyledSpan(
    boolean doSyntaxHighlighting,
    @NotNull StringBuilder buffer,
    @NotNull TextAttributesKey attributesKey,
    @Nullable String value
  ) {
    if (doSyntaxHighlighting) {
      HtmlSyntaxInfoUtil.appendStyledSpan(buffer, attributesKey, value);
    }
    else {
      buffer.append(value);
    }
  }

  private static void appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
    boolean doSyntaxHighlighting,
    @NotNull StringBuilder buffer,
    @NotNull Project project,
    @NotNull Language language,
    @Nullable String codeSnippet
  ) {
    if (doSyntaxHighlighting) {
      HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(buffer, project, language, codeSnippet);
    }
    else {
      buffer.append(codeSnippet);
    }
  }

  void generateAnnotation(
    StringBuilder buffer,
    AnnotationFormat format,
    boolean generateLink,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting
  ) {
    String qualifiedName = myAnnotation.getQualifiedName();
    PsiClassType type = myTargetClass != null && qualifiedName != null &&
                        JavaDocUtil.findReferenceTarget(myContext.getManager(), qualifiedName, myContext) != null
                        ? JavaPsiFacade.getElementFactory(myContext.getProject()).createType(myTargetClass, PsiSubstitutor.EMPTY)
                        : null;

    boolean red = type == null && !myResolveNotPossible && !isInferred() && !isExternal();

    boolean highlightNonCodeAnnotations = format == AnnotationFormat.ToolTip && (isInferred() || isExternal());
    if (highlightNonCodeAnnotations) buffer.append("<b>");
    if (isInferred()) buffer.append("<i>");
    if (red) buffer.append("<font color=red>");

    boolean forceShortNames = format != AnnotationFormat.JavaDocComplete;

    if (red) {
      buffer.append("@");
    }
    else {
      appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES, "@");
    }
    String name = forceShortNames ? myNameReference.getReferenceName() : myNameReference.getText();
    if (type != null && generateLink) {
      StringBuilder styledNameBuilder = new StringBuilder();
      appendStyledSpan(doSyntaxHighlighting, styledNameBuilder, JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES, name);
      String styledName = styledNameBuilder.toString();
      JavaDocInfoGeneratorFactory.create(
        myContext.getProject(),
        null,
        JavaDocHighlightingManagerImpl.getInstance(),
        isForRenderedDoc,
        doSyntaxHighlighting
      ).generateLink(buffer, myTargetClass, styledName, format == AnnotationFormat.JavaDocComplete);
    }
    else if (!red && name != null) {
      appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES, name);
    }
    if (red) buffer.append("</font>");

    generateAnnotationAttributes(buffer, generateLink, isForRenderedDoc, doSyntaxHighlighting);
    if (isInferred()) buffer.append("</i>");
    if (highlightNonCodeAnnotations) buffer.append("</b>");
  }

  private void generateAnnotationAttributes(
    StringBuilder buffer,
    boolean generateLink,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting
  ) {
    final PsiNameValuePair[] attributes = myAnnotation.getParameterList().getAttributes();
    if (attributes.length > 0) {
      appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.PARENTHESES, "(");
      boolean first = true;
      for (PsiNameValuePair pair : attributes) {
        if (!first) appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.COMMA, ",&nbsp;");
        first = false;
        generateAnnotationAttribute(buffer, generateLink, pair, isForRenderedDoc, doSyntaxHighlighting);
      }
      appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.PARENTHESES, ")");
    }
  }

  private static void generateAnnotationAttribute(
    StringBuilder buffer,
    boolean generateLink,
    PsiNameValuePair pair,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting
  ) {
    final String name = pair.getName();
    if (name != null) {
      appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.ANNOTATION_ATTRIBUTE_NAME_ATTRIBUTES, name);
      appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.OPERATION_SIGN, " = ");
    }
    final PsiAnnotationMemberValue value = pair.getValue();
    if (value != null) {
      if (value instanceof PsiArrayInitializerMemberValue) {
        appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.BRACES, "{");
        boolean firstMember = true;
        for (PsiAnnotationMemberValue memberValue : ((PsiArrayInitializerMemberValue)value).getInitializers()) {
          if (!firstMember) {
            appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.COMMA, ",");
          }
          firstMember = false;
          appendLinkOrText(buffer, memberValue, generateLink, isForRenderedDoc, doSyntaxHighlighting);
        }
        appendStyledSpan(doSyntaxHighlighting, buffer, JavaHighlightingColors.BRACES, "}");
      }
      else {
        appendLinkOrText(buffer, value, generateLink, isForRenderedDoc, doSyntaxHighlighting);
      }
    }
  }

  private static void appendLinkOrText(
    StringBuilder buffer,
    PsiAnnotationMemberValue memberValue,
    boolean generateLink,
    boolean isForRenderedDoc,
    boolean doSyntaxHighlighting
  ) {
    if (memberValue instanceof PsiQualifiedReferenceElement) {
      String text = ((PsiQualifiedReferenceElement)memberValue).getCanonicalText();
      PsiElement resolve = null;
      try {
        resolve = ((PsiQualifiedReferenceElement)memberValue).resolve();
      }
      catch (Exception e) {
        LOG.debug(e);
      }

      if (resolve instanceof PsiField) {
        PsiField field = (PsiField)resolve;
        PsiClass aClass = field.getContainingClass();

        if (generateLink) {
          int startOfPropertyNamePosition = text.lastIndexOf('.');
          if (startOfPropertyNamePosition != -1) {
            text = text.substring(0, startOfPropertyNamePosition) + '#' + text.substring(startOfPropertyNamePosition + 1);
          }
          else {
            if (aClass != null) text = aClass.getQualifiedName() + '#' + field.getName();
          }
          JavaDocInfoGeneratorFactory.create(
              field.getProject(), null, JavaDocHighlightingManagerImpl.getInstance(), isForRenderedDoc, doSyntaxHighlighting)
            .generateLink(buffer, text, aClass != null ? aClass.getName() + '.' + field.getName() : null, memberValue, false);
        }
        else {
          appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
            doSyntaxHighlighting,
            buffer,
            memberValue.getProject(),
            memberValue.getLanguage(),
            aClass != null ? aClass.getName() + '.' + field.getName() : memberValue.getText());
        }
        return;
      }
    }

    appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
      doSyntaxHighlighting,
      buffer,
      memberValue.getProject(),
      memberValue.getLanguage(),
      memberValue.getText());
  }

  public static List<AnnotationDocGenerator> getAnnotationsToShow(@NotNull PsiAnnotationOwner owner, @NotNull PsiElement context) {
    if (owner instanceof PsiModifierList) {
      return getAnnotationsToShow(((PsiModifierListOwner)((PsiModifierList)owner).getParent()));
    }
    Set<String> shownAnnotations = new HashSet<>();
    return ContainerUtil.mapNotNull(owner.getAnnotations(),
                                    annotation -> forAnnotation(context, shownAnnotations, annotation));
  }

  public static List<AnnotationDocGenerator> getAnnotationsToShow(@NotNull PsiModifierListOwner owner) {
    Set<String> shownAnnotations = new HashSet<>();
    return StreamEx.of(AnnotationUtil.getAllAnnotations(owner, false, null))
      .filter(owner instanceof PsiClass || owner instanceof PsiJavaModule ? anno -> true
                                                                          : anno -> !AnnotationTargetUtil.isTypeAnnotation(anno) ||
                                                                                    AnnotationUtil.isInferredAnnotation(anno) ||
                                                                                    AnnotationUtil.isExternalAnnotation(anno))
      .map(annotation -> forAnnotation(owner, shownAnnotations, annotation))
      .nonNull()
      .toList();
  }

  private static @Nullable AnnotationDocGenerator forAnnotation(@NotNull PsiElement context,
                                                                @NotNull Set<String> shownAnnotations,
                                                                @NotNull PsiAnnotation annotation) {
    PsiJavaCodeReferenceElement nameReferenceElement = annotation.getNameReferenceElement();
    if (nameReferenceElement == null) return null;

    AnnotationDocGenerator anno = new AnnotationDocGenerator(annotation, nameReferenceElement, context);
    if (anno.isNonDocumentedAnnotation()) return null;

    if (!(shownAnnotations.add(annotation.getQualifiedName()) ||
          JavaDocInfoGenerator.isRepeatableAnnotationType(nameReferenceElement.resolve()))) {
      return null;
    }
    return anno;
  }
}

enum AnnotationFormat {
  ToolTip, JavaDocShort, JavaDocComplete
}
