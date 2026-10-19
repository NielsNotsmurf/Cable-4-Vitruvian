package nl.nielsnotsmurf.cleanui.ui.tour

import nl.nielsnotsmurf.cleanui.ui.CleanRoutes

/**
 * One stop of the app tour: the screen it opens, the element it points at (see
 * [tourTarget]), and what it says. A step without a target shows its card in the middle.
 */
data class TourStep(
    val route: String,
    val target: String?,
    val title: String,
    val body: String
)

/** Keys of the elements the tour points at. */
object TourTargets {
    const val WHO = "who_is_training"
    const val CONNECTION = "connection"
    const val PICK_EXERCISE = "pick_exercise"
    const val TILES = "train_tiles"
    const val ONE_RM_TEST = "one_rm_test"
    const val SEARCH = "exercise_search"
    const val FAVOURITES = "favourites"
    const val ACCOUNT = "settings_account"
    const val SETTINGS_PROFILES = "settings_profiles"
    const val SETTINGS_HELP = "settings_help"
    const val HEART_RATE = "heart_rate_button"
    fun tab(route: String) = "tab:$route"
}

object AppTour {
    val steps: List<TourStep> = listOf(
        TourStep(
            CleanRoutes.TRAIN, null,
            "Welcome to Cable",
            "A quick look around. Tap Next to go on, or Skip tour to start straight away. " +
                "You can watch it again from Settings."
        ),
        TourStep(
            CleanRoutes.TRAIN, TourTargets.WHO,
            "Who's training",
            "Tap names to train together, up to 4 people. Sets rotate between them in this order. " +
                "A profile with a PIN asks for it before it joins."
        ),
        TourStep(
            CleanRoutes.TRAIN, TourTargets.CONNECTION,
            "Your trainer",
            "Shows whether the trainer is connected. Switch the trainer on and the app connects over " +
                "Bluetooth when you start a set."
        ),
        TourStep(
            CleanRoutes.TRAIN, TourTargets.PICK_EXERCISE,
            "Build a workout",
            "Pick one or more muscle groups and how long you have. The app picks the exercises, keeps " +
                "the same equipment together and loads each lifter at a % of their 1RM. Swap any exercise."
        ),
        TourStep(
            CleanRoutes.TRAIN, TourTargets.TILES,
            "One exercise or Just Lift",
            "One exercise opens the library and Set setup for a single lift. Just Lift starts the " +
                "trainer straight away with no plan."
        ),
        TourStep(
            CleanRoutes.TRAIN, TourTargets.ONE_RM_TEST,
            "Find your 1RM",
            "The 1RM test shows how each lift is done, then goes up one rep at a time until a miss. " +
                "Your heaviest rep becomes your 1RM, and all loads follow from it."
        ),
        TourStep(
            CleanRoutes.TRAIN, TourTargets.tab(CleanRoutes.HISTORY),
            "History",
            "Every workout with sets, loads and heart rate. Tap one for the details."
        ),
        TourStep(
            CleanRoutes.TRAIN, TourTargets.tab(CleanRoutes.ANALYTICS),
            "Analytics",
            "Your week, streaks, 1RMs and records, with charts per exercise."
        ),
        TourStep(
            CleanRoutes.EXERCISES, TourTargets.SEARCH,
            "Exercises",
            "Search the library or filter by muscle and equipment. Tap ▶ on an exercise for a demo video."
        ),
        TourStep(
            CleanRoutes.EXERCISES, TourTargets.FAVOURITES,
            "Favourites",
            "Star the exercises you do most, then show only those with this chip."
        ),
        TourStep(
            CleanRoutes.SETTINGS, TourTargets.ACCOUNT,
            "Your account",
            "Set a PIN to keep your history and 1RMs private on a shared phone, or switch account here."
        ),
        TourStep(
            CleanRoutes.SETTINGS, TourTargets.SETTINGS_PROFILES,
            "Profiles and heart rate",
            "Add the people you train with, and pair a heart rate sensor for each of them."
        ),
        TourStep(
            CleanRoutes.PROFILES, TourTargets.HEART_RATE,
            "Garmin heart rate",
            "On your Garmin, turn on Broadcast Heart Rate (in the controls menu, or under Sensors " +
                "and Accessories → Wrist Heart Rate). Then tap ♥ here and pick your watch. A chest " +
                "strap works the same way."
        ),
        TourStep(
            CleanRoutes.SETTINGS, TourTargets.SETTINGS_HELP,
            "Watch this again",
            "The tour and the heart rate guide live here. That's it, have a good session!"
        )
    )
}
