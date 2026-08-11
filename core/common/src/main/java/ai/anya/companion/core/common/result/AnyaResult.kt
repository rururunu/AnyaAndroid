package ai.anya.companion.core.common.result

/** Functional result type for domain/data boundaries. */
public sealed class AnyaResult<out T> {
    public data class Success<T>(public val data: T) : AnyaResult<T>()
    public data class Failure(
        public val error: AnyaError,
    ) : AnyaResult<Nothing>()

    public inline fun <R> map(transform: (T) -> R): AnyaResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    public inline fun onSuccess(block: (T) -> Unit): AnyaResult<T> {
        if (this is Success) block(data)
        return this
    }

    public inline fun onFailure(block: (AnyaError) -> Unit): AnyaResult<T> {
        if (this is Failure) block(error)
        return this
    }
}

public sealed class AnyaError {
    public data class Network(public val message: String, public val cause: Throwable? = null) : AnyaError()
    public data class Unauthorized(public val message: String = "Unauthorized") : AnyaError()
    public data class Protocol(public val message: String) : AnyaError()
    public data class NotPaired(public val message: String = "Device is not paired") : AnyaError()
    public data class Unknown(public val message: String, public val cause: Throwable? = null) : AnyaError()
}

public inline fun <T> runCatchingAnya(block: () -> T): AnyaResult<T> = try {
    AnyaResult.Success(block())
} catch (t: Throwable) {
    AnyaResult.Failure(AnyaError.Unknown(t.message ?: "Unknown error", t))
}
