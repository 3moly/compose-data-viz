package com.the3moly.buildtools.ktlintrules

import com.pinterest.ktlint.rule.engine.core.api.ElementType
import com.pinterest.ktlint.rule.engine.core.api.Rule
import com.pinterest.ktlint.rule.engine.core.api.RuleId
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class StringLiteralRule : Rule(RuleId(Constants.RAW_STRING_RULE_ID), About()) {

    // New helper to detect require block
    private fun ASTNode.isInsideRequireBlock(): Boolean {
        var current: ASTNode? = this.treeParent
        while (current != null) {
            val psi = current.psi
            if (psi is KtCallExpression && psi.calleeExpression?.text == "require") {
                return true
            }
            current = current.treeParent
        }
        return false
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun beforeVisitChildNodes(
        node: ASTNode,
        autoCorrect: Boolean,
        emit: (offset: Int, errorMessage: String, canBeAutoCorrected: Boolean) -> Unit,
    ) {
        val text = node.text
        val shouldEmit =
            when {
                node.elementType != ElementType.STRING_TEMPLATE -> false

                text == Constants.EMPTY_DOUBLE_QUOTE || text == Constants.EMPTY_TRIPLE_QUOTE -> false

                node.psi
                    .getParentOfType<KtProperty>(false)
                    ?.hasModifier(KtTokens.CONST_KEYWORD) == true -> false

                // ADDED: Check if inside a require() block
                node.isInsideRequireBlock() -> false

                (node.psi as? KtStringTemplateExpression)?.let { psi ->
                    psi.entries.any { it.expression != null } || psi.getParentOfType<KtAnnotationEntry>(
                        true,
                    ) != null
                } ?: true -> false

                // Traverse using ASTNode treeParent instead of PSI to ensure we reach the builder call
                node.isInsideStringBuilder() -> false

                else -> true
            }

        if (shouldEmit) {
            emit(
                node.startOffset,
                Constants.RAW_STRING_RULE_DESCRIPTION,
                false,
            )
        }
    }

    private fun ASTNode.isInsideStringBuilder(): Boolean {
        // Use KtLint's native ASTNode.treeParent to avoid broken PSI parent pointers
        var current: ASTNode? = this.treeParent
        while (current != null) {
            val psi = current.psi
            if (psi is KtCallExpression) {
                if (psi.calleeExpression?.text in STRING_BUILDER_CALLEES) {
                    return true
                }
            }
            current = current.treeParent
        }
        return false
    }

    private companion object {
        val STRING_BUILDER_CALLEES =
            setOf(
                "append",
                "appendLine",
                "appendText",
                "buildString",
                "id",
                "PrimitiveSerialDescriptor",
                "setFloatUniform",
                "subcompose"
            )
    }
}
