Breezy's Cheat Station — Settings UI polish

Contains a replacement SettingsActivity.kt based on the source archive provided for this turn.
Changes are presentation-only: tighter vertical rhythm, consistent horizontal content padding,
slightly smaller/letter-spaced section labels, and cleaner spacing for lines, buttons, and cards.
Existing settings actions and navigation are preserved.

Apply from the project root by extracting this ZIP. Back up SettingsActivity.kt first.
Then run git diff --check and ./gradlew assembleDebug. This patch has not been compiled against
the full Android project in this environment.
