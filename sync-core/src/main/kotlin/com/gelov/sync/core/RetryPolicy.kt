import java.time.Duration
import kotlin.math.min
import kotlin.random.Random

data class RetryPolicy(
    val base: Duration = Duration.ofSeconds(2),
    val max: Duration = Duration.ofMinutes(2),
    val jitterRatio: Double = 0.2,     // ±20%
    val maxAttempts: Int = 8
) {
    fun nextDelay(attempt: Int): Duration {
        // attempt: 1,2,3...
        val pow = 1L shl min(attempt - 1, 20) // clamp
        val rawMillis = base.toMillis() * pow
        val capped = min(rawMillis, max.toMillis())

        val jitter = (capped * jitterRatio).toLong()
        val delta = if (jitter <= 0) 0L else Random.nextLong(-jitter, jitter + 1)

        return Duration.ofMillis((capped + delta).coerceAtLeast(0))
    }
}
