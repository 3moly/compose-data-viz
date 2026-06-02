import com.pinterest.ktlint.test.KtLintAssertThat.Companion.assertThatRule
import com.the3moly.buildtools.ktlintrules.Constants
import com.the3moly.buildtools.ktlintrules.StringLiteralRule
import kotlin.test.Test

class StringLiteralRuleTest {
    private val stringRuleAssertThat = assertThatRule { StringLiteralRule() }

    @Test
    fun `should report error on standard string literal in function`() {
        val code =
            """
            fun greet() {
                println("Hello, world!")
            }
            """.trimIndent()

        stringRuleAssertThat(code)
            .hasLintViolationWithoutAutoCorrect(
                2,
                13,
                Constants.RAW_STRING_RULE_DESCRIPTION,
            )
    }

    @Test
    fun `should report error on standard string literal in property`() {
        val code =
            """
            val greeting = "Hello, world!"
            """.trimIndent()

        stringRuleAssertThat(code)
            .hasLintViolationWithoutAutoCorrect(
                1,
                16,
                Constants.RAW_STRING_RULE_DESCRIPTION,
            )
    }

    @Test
    fun `should report error on triple quoted raw string literal`() {
        val code =
            """
            val query = ""${'"'}
                SELECT * FROM table
            ""${'"'}.trimIndent()
            """.trimIndent()

        stringRuleAssertThat(code)
            .hasLintViolationWithoutAutoCorrect(
                1,
                13,
                Constants.RAW_STRING_RULE_DESCRIPTION,
            )
    }

    @Test
    fun `should not report error on const val`() {
        val code =
            """
            const val GREETING = "Hello, world!"
            """.trimIndent()

        stringRuleAssertThat(code).hasNoLintViolations()
    }

    @Test
    fun `should not report error on empty strings`() {
        val code =
            """
            val emptySingle = ""
            val emptyTriple = ""${'\"'}""${'\"'}
            """.trimIndent()

        stringRuleAssertThat(code).hasNoLintViolations()
    }

    @Test
    fun `should not report error on string templates with variables or expressions`() {
        val code =
            """
            fun greet(name: String) {
                val simple = "Hello ${'$'}name"
                val complex = "Length is ${'$'}{name.length}"
            }
            """.trimIndent()

        stringRuleAssertThat(code).hasNoLintViolations()
    }

    @Test
    fun `should not report error on string literals inside annotations`() {
        val code =
            """
            @Deprecated(message = "Use newFunction instead")
            @JvmName("CustomJvmName")
            fun oldFunction() {}
            """.trimIndent()

        stringRuleAssertThat(code).hasNoLintViolations()
    }

    @Test
    fun `should not report error on string literals inside string builders`() {
        val code =
            """
            fun createString(): String {
                return buildString {
                    append("Part 1 ")
                    appendLine("Part 2")
                    appendText("Part 3")
                }
            }
            """.trimIndent()

        stringRuleAssertThat(code).hasNoLintViolations()
    }
}
