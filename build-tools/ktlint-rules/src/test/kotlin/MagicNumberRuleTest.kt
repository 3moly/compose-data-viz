import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.the3moly.build_tools.ktlint_rules.Constants
import com.the3moly.build_tools.ktlint_rules.MagicNumberRule
import kotlin.test.Test

class MagicNumberRuleTest {
    private val magicNumberRuleAssertThat = assertThatRule { MagicNumberRule() }

    @Test
    fun `should report error when magic number is used`() {
        val code =
            """
            fun calculate() {
                val result = 42
                println(result)
            }
            """.trimIndent()
        val line = 2
        val column = 18
        magicNumberRuleAssertThat(code)
            .hasLintViolationWithoutAutoCorrect(
                line,
                column,
                Constants.MAGIC_NUMBERS_RULE_DESCRIPTION,
            )
    }

    @Test
    fun `should not report error when allowed number is used`() {
        val code =
            """
            fun calculate() {
                val defaultValue = 1
                println(defaultValue)
            }
            """.trimIndent()

        magicNumberRuleAssertThat(code).hasNoLintViolations()
    }
}