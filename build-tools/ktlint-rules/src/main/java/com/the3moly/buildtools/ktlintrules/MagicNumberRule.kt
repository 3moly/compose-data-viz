package com.the3moly.buildtools.ktlintrules

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class MagicNumberRule : Rule(RuleId(Constants.MAGIC_NUMBERS_RULE_ID), About()) {
    private val allowedNumbers =
        setOf(
            Constants.ALLOWED_INT_ZERO,
            Constants.ALLOWED_INT_ONE,
            Constants.ALLOWED_INT_MINUS_ONE,
            Constants.ALLOWED_FLOAT_ONE,
            Constants.ALLOWED_FLOAT_ZERO,
            Constants.ALLOWED_LONG_ZERO,
            Constants.ALLOWED_INT_TWO,
            Constants.ALLOWED_FLOAT_TWO,
            Constants.ALLOWED_FLOAT_HALF_ONE,
        )

    @Suppress("OVERRIDE_DEPRECATION")
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        if (node.elementType != ElementType.INTEGER_CONSTANT && node.elementType != ElementType.FLOAT_CONSTANT) {
            return
        }

        val parameter = node.psi.getParentOfType<KtParameter>(true)
        val isConstructorDefault = parameter?.parent?.parent is KtPrimaryConstructor

        if (isConstructorDefault) {
            return // Whitelist default values in constructors
        }

        val text = node.text
        val property = node.psi.getParentOfType<KtProperty>(false)
        if (property?.hasModifier(KtTokens.CONST_KEYWORD) == true) {
            return
        }

        val valueArgument = node.psi.getParentOfType<KtValueArgument>(true)
        val isNamedArgument = valueArgument?.getArgumentName() != null

        if (isNamedArgument) {
            return
        }


        val dotExpression = node.psi.parent as? KtDotQualifiedExpression
        val isComposeUnit = dotExpression?.selectorExpression?.text in setOf("dp", "sp", "px", "em")
        if (isComposeUnit) {
            return
        }

        val callExpression = node.psi.getParentOfType<KtCallExpression>(true)
        val isColorCall = callExpression?.calleeExpression?.text == Constants.COLOR_CALL_NAME
        val isHexColorLiteral = text.startsWith(Constants.HEX_COLOR_PREFIX, ignoreCase = true)
        if (isColorCall && isHexColorLiteral) {
            return
        }


        if (text !in allowedNumbers) {
            emit(
                node.startOffset,
                buildErrorMessage(node, text, property, callExpression),
                false,
            )
        }

        if (text !in allowedNumbers) {
            emit(
                node.startOffset,
                buildErrorMessage(node, text, property, callExpression),
                false,
            )
        }
    }

    private fun buildErrorMessage(
        node: ASTNode,
        number: String,
        property: KtProperty?,
        callExpression: KtCallExpression?,
    ): String {
        val typeLabel =
            if (node.elementType == ElementType.FLOAT_CONSTANT) {
                Constants.FLOAT_TYPE_LABEL
            } else {
                Constants.INTEGER_TYPE_LABEL
            }

        val context = describeContext(node, property, callExpression)

        return String.format(Constants.MAGIC_NUMBER_MESSAGE_TEMPLATE, number, typeLabel, context)
    }

    private fun describeContext(
        node: ASTNode,
        property: KtProperty?,
        callExpression: KtCallExpression?,
    ): String {
        val valueArgument = node.psi.getParentOfType<KtValueArgument>(true)

        // Prefer describing the call site, since `val x = foo(42)` is more usefully
        // reported as "argument to foo()" than as "assigned to x".
        if (callExpression != null && valueArgument != null) {
            val callee = callExpression.calleeExpression?.text ?: Constants.UNKNOWN_CALLEE
            val argumentName = valueArgument.getArgumentName()?.asName?.asString()
            if (argumentName != null) {
                return String.format(Constants.CONTEXT_NAMED_ARGUMENT, argumentName, callee)
            }

            val argumentList = valueArgument.parent as? KtValueArgumentList
            val position = argumentList?.arguments?.indexOf(valueArgument) ?: -1
            if (position >= 0) {
                return String.format(Constants.CONTEXT_POSITIONAL_ARGUMENT, position + 1, callee)
            }

            return String.format(Constants.CONTEXT_CALL, callee)
        }

        val propertyName = property?.name
        if (propertyName != null) {
            return String.format(Constants.CONTEXT_PROPERTY, propertyName)
        }

        return Constants.CONTEXT_NONE
    }
}
