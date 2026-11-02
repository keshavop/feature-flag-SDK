package io.github.keshavop.featureflags.core.eval

/** Result of an evaluation: the value, which variation produced it, and why. */
public data class EvaluationDetail<out T>(
    val value: T,
    val variationIndex: Int?,
    val reason: EvaluationReason,
) {
    val isDefaultValue: Boolean get() = variationIndex == null
}

/** Why an evaluation produced its value (LLD §2.2). [kind] is the stable wire name. */
public sealed interface EvaluationReason {
    public val kind: String

    public data object Off : EvaluationReason {
        override val kind: String = "OFF"
    }

    public data object TargetMatch : EvaluationReason {
        override val kind: String = "TARGET_MATCH"
    }

    public data class RuleMatch(
        val ruleIndex: Int,
        val ruleId: String,
        val inExperiment: Boolean = false,
    ) : EvaluationReason {
        override val kind: String = "RULE_MATCH"
    }

    public data class Fallthrough(
        val inExperiment: Boolean = false,
    ) : EvaluationReason {
        override val kind: String = "FALLTHROUGH"
    }

    public data class PrerequisiteFailed(
        val prerequisiteKey: String,
    ) : EvaluationReason {
        override val kind: String = "PREREQUISITE_FAILED"
    }

    public data class Error(
        val errorKind: ErrorKind,
    ) : EvaluationReason {
        override val kind: String = "ERROR"
    }

    public companion object {
        internal val FALLTHROUGH: Fallthrough = Fallthrough(false)
        internal val FALLTHROUGH_EXPERIMENT: Fallthrough = Fallthrough(true)
        private val errors = ErrorKind.entries.associateWith { Error(it) }

        /** Shared instances, so error paths do not allocate. */
        public fun error(kind: ErrorKind): Error = errors.getValue(kind)
    }
}

public enum class ErrorKind {
    CLIENT_NOT_READY,
    FLAG_NOT_FOUND,
    WRONG_TYPE,
    MALFORMED_FLAG,
    USER_NOT_SPECIFIED,
    EXCEPTION,
}
