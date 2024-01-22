package io.kotest.plugin.intellij.run

import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.kotest.plugin.intellij.KotestConfigurationFactory
import io.kotest.plugin.intellij.KotestConfigurationType
import io.kotest.plugin.intellij.KotestRunConfiguration
import io.kotest.plugin.intellij.psi.asKtClassOrObjectOrNull
import io.kotest.plugin.intellij.psi.enclosingKtClassOrObject
import io.kotest.plugin.intellij.psi.isRunnableSpec
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.codeInsight.tooling.KotlinNativeRunConfigurationProvider
import org.jetbrains.kotlin.idea.gradleJava.run.AbstractKotlinMultiplatformTestClassGradleConfigurationProducer
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class SpecRunConfigurationProducer: AbstractKotestGradleConfigurationProducer() {
   override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isJvm() || platform.isCommon() || platform.isNative()

   override fun resolveTarget(sourceElement: PsiElement?): TestTarget? {
      val element = sourceElement ?: return null
      val psiClass = element.enclosingKtClassOrObject()?.toLightClass() ?: return null

      return TestTarget.TestClass(psiClass)
   }
}
