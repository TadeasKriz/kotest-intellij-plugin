package io.kotest.plugin.intellij.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import io.kotest.plugin.intellij.Test
import io.kotest.plugin.intellij.TestType
import org.jetbrains.kotlin.idea.base.facet.isNewMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.gradleJava.run.AbstractKotlinTestClassGradleConfigurationProducer
import org.jetbrains.kotlin.idea.gradleJava.run.MultiplatformTestTasksChooser
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.plugins.gradle.execution.test.runner.applyTestConfiguration
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.createTestFilterFrom

abstract class AbstractKotestGradleConfigurationProducer: AbstractKotlinTestClassGradleConfigurationProducer() {
   companion object {
      private val KOTEST_TARGET: Key<TestTarget> = Key.create("KOTEST_TARGET")
   }

   override val forceGradleRunner: Boolean get() = true
   override val hasTestFramework: Boolean get() = true

   private val mppTestTasksChooser = MultiplatformTestTasksChooser()

   abstract fun isApplicable(module: Module, platform: TargetPlatform): Boolean

   abstract fun resolveTarget(
       sourceElement: PsiElement?,
   ): TestTarget?

   override fun setupConfigurationFromContext(
       configuration: GradleRunConfiguration,
       context: ConfigurationContext,
       sourceElement: Ref<PsiElement>
   ): Boolean {
      val target = resolveTarget(sourceElement.get()) ?: return false
      configuration.putUserData<TestTarget>(KOTEST_TARGET, target)

      val setupResult = super.setupConfigurationFromContext(configuration, context, sourceElement)
      if (setupResult) {
         when (target) {
            is TestTarget.TestClass -> {
               configuration.setSpec(target.psiClass)
            }
            is TestTarget.TestPath -> {
               configuration.setSpec(target.psiClass)
               configuration.setTest(target.test)
            }
         }
         configuration.name = target.configurationName
      }
      return setupResult
   }

   override fun isConfigurationFromContext(
       configuration: GradleRunConfiguration,
       context: ConfigurationContext
   ): Boolean {
      val configurationTarget = configuration[KOTEST_TARGET]
      val contextTarget = resolveTarget(context.psiLocation)

      return if (configurationTarget != null && contextTarget != null) {
         configurationTarget == contextTarget
      } else {
         super.isConfigurationFromContext(configuration, context)
      }
   }

   final override fun isApplicable(module: Module): Boolean {
      if (!module.isNewMultiPlatformModule) {
         return false
      }

      // TODO: Check why Kotlin is using elvis here
      val platform = module.platform ?: return false
      return isApplicable(module, platform)
   }

   override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {

      return other.isProducedBy(this::class.java) || other.isProducedBy(AbstractKotlinTestClassGradleConfigurationProducer::class.java) || super.isPreferredConfiguration(self, other)
   }

   override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
      return other.isProducedBy(this::class.java) || super.shouldReplace(self, other)
   }

   override fun getAllTestsTaskToRun(
       context: ConfigurationContext,
       element: PsiClass,
       chosenElements: List<PsiClass>,
   ): List<TestTasksToRun> {
      val tasks = mppTestTasksChooser.listAvailableTasks(listOf(element))
      val wildcardFilter = createTestFilterFrom(element)
      return tasks.map { TestTasksToRun(it, wildcardFilter) }
   }

   override fun onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable) {
      // TODO: Add support for running tests in abstract classes
      // val inheritorChooser: InheritorChooser = object : InheritorChooser() {
      //    override fun runForClasses(classes: List<PsiClass>, method: PsiMethod?, context: ConfigurationContext, runnable: Runnable) {
      //       chooseTestClassConfiguration(configuration, context, runnable, classes)
      //    }
      //
      //    override fun runForClass(aClass: PsiClass, psiMethod: PsiMethod?, context: ConfigurationContext, runnable: Runnable) {
      //       chooseTestClassConfiguration(configuration, context, runnable, listOf(aClass))
      //    }
      // }

      val runConfiguration = configuration.configuration as GradleRunConfiguration
      val target = runConfiguration[KOTEST_TARGET] ?: error("Kotest Target not present in GradleRunConfiguration")
      // TODO: Add support for running tests in abstract classes
      // val sourceElement = fromContext.sourceElement as PsiClass
      // if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, null, sourceElement)) {
      //    return
      // }

      chooseTestClassConfiguration(configuration, context, startRunnable, target)
   }

   private fun chooseTestClassConfiguration(
       fromContext: ConfigurationFromContext,
       context: ConfigurationContext,
       performRunnable: Runnable,
       target: TestTarget,
   ) {
      val locationName = target.psiClass.name
      val dataContext = MultiplatformTestTasksChooser.createContext(context.dataContext, locationName)

      // TODO: Should we pass either `testElement` or `psiClass` depending on target, or just `psiClass` each time?
      //   AFAIK it's only used to resolve `SourceFile` so `psiClass` is probably better anyway.
      mppTestTasksChooser.multiplatformChooseTasks(context.project, dataContext, listOf(target.psiClass)) { tasks ->
         val configuration = fromContext.configuration as GradleRunConfiguration
         val settings = configuration.settings

         val createFilter = { clazz: PsiClass -> createTestFilterFrom(clazz) }
         if (!settings.applyTestConfiguration(context.module, tasks, listOf(target.psiClass), createFilter)) {
            LOG.warn("Cannot apply class test configuration, uses raw run configuration")
            performRunnable.run()
         }
         val distinctTaskNames = tasks.flatMap { it.map { it.value.testName } }.distinct()
         if (distinctTaskNames.isNotEmpty()) {
            configuration.name = target.configurationName + distinctTaskNames.joinToString(prefix = " [", postfix = "]")
         }
         settings.externalProjectPath = ExternalSystemApiUtil.getExternalProjectPath(context.module)
         performRunnable.run()
      }
   }

   private fun GradleRunConfiguration.setSpec(psiClass: PsiClass) {
      val specRegex = psiClass.name?.let { "*" + Regex.escape(it) }
      settings.env["kotest_filter_specs"] = specRegex
      settings.env["SIMCTL_CHILD_kotest_filter_specs"] = specRegex
   }

   private fun GradleRunConfiguration.setTest(test: Test) {
      fun Test.buildRegex(nestedRegex: String?): String {
         val escapedTestName = Regex.escape(name.name)
         val thisRegex = when (testType) {
            TestType.Container -> "${escapedTestName}( -- ${nestedRegex ?: "*"})?"
            TestType.Test -> escapedTestName
         }
         return parent?.buildRegex(thisRegex) ?: thisRegex
      }

      val testRegex = test.buildRegex(null)
      settings.env["kotest_filter_tests"] = testRegex
      settings.env["SIMCTL_CHILD_kotest_filter_tests"] = testRegex
   }

   private operator fun <T> GradleRunConfiguration.get(key: Key<T>): T? {
      return getUserData<T>(key) as T?
   }

   private operator fun <T> GradleRunConfiguration.set(key: Key<T>, value: T) {
      return putUserData<T>(key, value)
   }
}
