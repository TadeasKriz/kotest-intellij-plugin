package io.kotest.plugin.intellij.run

import com.intellij.execution.Location
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import io.kotest.plugin.intellij.Test
import io.kotest.plugin.intellij.psi.enclosingKtClassOrObject
import io.kotest.plugin.intellij.styles.SpecStyle
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.isNative
import org.jetbrains.kotlin.psi.KtClass

class TestPathRunConfigurationProducer: AbstractKotestGradleConfigurationProducer() {
   override fun isApplicable(module: Module, platform: TargetPlatform) = platform.isCommon() || platform.isJvm() || platform.isNative()

   override fun resolveTarget(
      sourceElement: PsiElement?
   ): TestTarget? {
      val element = sourceElement ?: return null
      val test = findTest(element) ?: return null
      val psiClass = element.enclosingKtClassOrObject()?.toLightClass() ?: return null

      return TestTarget.TestPath(psiClass, test)
   }

   override fun getPsiClassForLocation(contextLocation: Location<*>): PsiClass? {
      val leaf = contextLocation.psiElement
      if (leaf is LeafPsiElement) {
         val spec = leaf.enclosingKtClassOrObject()
         if (spec is KtClass) {
            return spec.toLightClass()
         }
      }
      return super.getPsiClassForLocation(contextLocation)
   }

   private fun findTest(element: PsiElement): Test? {
      return SpecStyle.styles.asSequence()
         .filter { it.isContainedInSpec(element) }
         .mapNotNull { it.findAssociatedTest(element) }
         .firstOrNull()
   }
}
