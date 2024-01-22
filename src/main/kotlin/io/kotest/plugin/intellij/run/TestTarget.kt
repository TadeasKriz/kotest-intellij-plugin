package io.kotest.plugin.intellij.run

import com.intellij.psi.PsiClass
import io.kotest.plugin.intellij.Test

sealed interface TestTarget {
   val psiClass: PsiClass
   val configurationName: String

   data class TestClass(override val psiClass: PsiClass): TestTarget {
      override val configurationName: String = psiClass.name?.let { "$it." } ?: "<error>"
   }

   data class TestPath(override val psiClass: PsiClass, val test: Test): TestTarget {
      override val configurationName: String = listOf(
         psiClass.name?.let { "$it." } ?: "<error>",
         test.readableTestPath()
      ).joinToString("")
   }
}
