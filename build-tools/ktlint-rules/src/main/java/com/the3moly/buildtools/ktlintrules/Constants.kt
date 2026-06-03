package com.the3moly.buildtools.ktlintrules

object Constants {
    const val TODO = "TODO"
    const val TODO_RULE_ID = "custom:todo-rule"
    const val TODO_RULE_DESCRIPTION = "TODO comments are not allowed."
    const val MAGIC_NUMBERS_RULE_ID = "custom:magic-numbers-rule"
    const val MAGIC_NUMBERS_RULE_DESCRIPTION =
        "Magic numbers are not allowed. Use constants instead."
    const val RAW_STRING_RULE_ID = "custom:raw-string-rule"
    const val RAW_STRING_RULE_DESCRIPTION =
        "Avoid using raw string literals. Use const val instead."
    const val CUSTOM_RULES_GROUP = "custom-ktlint-rules"

    const val EMPTY_DOUBLE_QUOTE = "\"\""
    const val EMPTY_TRIPLE_QUOTE = "\"\"\"\"\"\""

    const val ALLOWED_INT_MINUS_ONE = "-1"
    const val ALLOWED_INT_ZERO = "0"
    const val ALLOWED_FLOAT_ZERO = "0f"
    const val ALLOWED_LONG_ZERO = "0L"
    const val ALLOWED_INT_ONE = "1"
    const val ALLOWED_FLOAT_ONE = "1f"
    const val ALLOWED_INT_TWO = "2"
    const val ALLOWED_FLOAT_TWO = "2f"
    const val ALLOWED_FLOAT_HALF_ONE = "0.5f"



    const val COLOR_CALL_NAME = "Color"
    const val HEX_COLOR_PREFIX = "0xFF"
    const val INTEGER_TYPE_LABEL = "integer"
    const val FLOAT_TYPE_LABEL = "float"
    const val UNKNOWN_CALLEE = "function"
    const val CONTEXT_NONE = ""

    const val MAGIC_NUMBER_MESSAGE_TEMPLATE =
        "Magic number '%s' (%s literal) is not allowed.%s Replace it with a named constant (e.g. const val)."
    const val CONTEXT_PROPERTY = " It is assigned to property '%s'."
    const val CONTEXT_NAMED_ARGUMENT = " It is passed as argument '%s' to '%s()'."
    const val CONTEXT_POSITIONAL_ARGUMENT = " It is passed as positional argument #%d to '%s()'."
    const val CONTEXT_CALL = " It is passed to '%s()'."

    const val PRECONDITION_REQUIRE = "require"
    const val PRECONDITION_REQUIRE_NOT_NULL = "requireNotNull"
    const val PRECONDITION_CHECK = "check"
    const val PRECONDITION_CHECK_NOT_NULL = "checkNotNull"
    const val PRECONDITION_ERROR = "error"
    const val PRECONDITION_ASSERT = "assert"
}
