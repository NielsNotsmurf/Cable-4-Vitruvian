package nl.nielsnotsmurf.cleanui.ui

/** Whether a profile has had the app tour. */
sealed interface TourStatus {
    data object NotSeen : TourStatus
    data class Done(val at: Long) : TourStatus
    data class Skipped(val at: Long) : TourStatus

    companion object {
        fun parse(stored: String?): TourStatus {
            val at = stored?.substringAfter(':', "")?.toLongOrNull() ?: 0L
            return when (stored?.substringBefore(':')) {
                "done" -> Done(at)
                "skipped" -> Skipped(at)
                else -> NotSeen
            }
        }

        fun encode(done: Boolean, at: Long): String = (if (done) "done:" else "skipped:") + at
    }
}

/**
 * Local accounts: every profile is an account, and a PIN makes it private. The app opens as
 * the account that signed in last; if that account has a PIN, it asks for it first.
 *
 * A PIN-protected profile's history only shows once that profile is unlocked on this phone
 * (signed in, or its PIN typed to join a training).
 */
data class AccountState(
    val onboarded: Boolean,
    val signedInId: String?,
    val lastAccountId: String?,
    val protectedIds: Set<String>,
    val unlockedIds: Set<String>,
    val tours: Map<String, TourStatus>
) {
    fun hasPin(lifterId: String): Boolean = lifterId in protectedIds
    fun canSee(lifterId: String): Boolean = lifterId !in protectedIds || lifterId in unlockedIds
    fun needsPin(lifterId: String): Boolean = !canSee(lifterId)
    fun tour(lifterId: String): TourStatus = tours[lifterId] ?: TourStatus.NotSeen
}
