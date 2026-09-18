package io.github.mimimishkin.jni.binding.consumer.fir

import io.github.mimimishkin.jni.binding.consumer.JniBindingConsumerErrors.NON_EXTERNAL_JNI_EXPECT
import io.github.mimimishkin.jni.binding.consumer.Symbols
import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirFunctionChecker
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.FirConstructor
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.declarations.toAnnotationClassId
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.symbols.SymbolInternals

private val JNI_EXPECT_FQNAME = Symbols.JniExpect.asSingleFqName()

/**
 * This is the only diagnostic that can be reported at the frontend level: whether a declaration explicitly annotated
 * `@JniExpect` is `external`. Everything else ([io.github.mimimishkin.jni.binding.consumer.jvm.JniExpectMatcher])
 * has to stay at the IR/codegen level, because it depends on the backend-resolved JVM type names and the
 * cross-module `actuals.json`, which do not exist during FIR.
 */
public class JniBindingUseChecker(session: FirSession) : FirAdditionalCheckersExtension(session) {

    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        override val functionCheckers: Set<FirFunctionChecker>
            get() = setOf(FunctionDeclarationChecker)

        override val propertyCheckers: Set<FirPropertyChecker>
            get() = setOf(PropertyDeclarationChecker)
    }

    private val jniExpectPredicate = LookupPredicate.create {
        annotated(JNI_EXPECT_FQNAME)
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(jniExpectPredicate)
    }

    private object FunctionDeclarationChecker : FirFunctionChecker(MppCheckerKind.Common) {
        @OptIn(SymbolInternals::class)
        context(context: CheckerContext, reporter: DiagnosticReporter)
        override fun check(declaration: FirFunction) {
            if (declaration is FirConstructor) return
            if (declaration is FirPropertyAccessor) {
                // Report the accessor only when the annotation is on the accessor itself (`@get:`/`@set:` use-site
                // target). A bare `@JniExpect` dwells on the property and is reported once by the property checker;
                // the checker also runs on the propagated accessors, and those must stay silent to avoid
                // double-reporting.
                val annotationOnAccessor = declaration.annotations.hasJniExpect(context.session)
                val annotationOnProperty = declaration.propertySymbol.fir.annotations.hasJniExpect(context.session)
                if (!declaration.status.isExternal && annotationOnAccessor && !annotationOnProperty) {
                    reporter.reportOn(declaration.source, NON_EXTERNAL_JNI_EXPECT, context)
                }
                return
            }
            if (declaration.annotations.hasJniExpect(context.session) && !declaration.status.isExternal) {
                reporter.reportOn(declaration.source, NON_EXTERNAL_JNI_EXPECT, context)
            }
        }
    }

    private object PropertyDeclarationChecker : FirPropertyChecker(MppCheckerKind.Common) {
        context(context: CheckerContext, reporter: DiagnosticReporter)
        override fun check(declaration: FirProperty) {
            if (!declaration.annotations.hasJniExpect(context.session)) return
            // On the JVM `external` cannot be applied to a property itself (`external get`/`external set` are the
            // only legal spellings of a native accessor), so a property expect is valid exactly when every accessor
            // is external. Report only when the property lacks external accessors.
            val getterIsExternal = declaration.getter?.status?.isExternal == true
            val setterIsExternal = declaration.setter?.status?.isExternal == true
            if (!getterIsExternal || (declaration.isVar && !setterIsExternal)) {
                reporter.reportOn(declaration.source, NON_EXTERNAL_JNI_EXPECT, context)
            }
        }
    }
}

private fun FirAnnotation.isJniExpect(session: FirSession): Boolean =
    toAnnotationClassId(session)?.asSingleFqName() == JNI_EXPECT_FQNAME

private fun List<FirAnnotation>.hasJniExpect(session: FirSession): Boolean = any { it.isJniExpect(session) }