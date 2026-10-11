Breezy's Cheat Station — hybrid visual foundation (Stage 1)

Style 3 console-coloured banners + Style 2 grouped settings.
This is a VISUAL FOUNDATION, not the complete illustrated game-library redesign.
No emulator, cheat, storage, repository, or backup logic is changed.
System glyphs are temporary text-based badges, NOT illustrated console artwork.

Apply from the project root:
  tar -czf ~/storage/downloads/Breezy-Pre-Visual-Foundation.tar.gz app/src/main
  unzip -o ~/storage/downloads/Breezy-Hybrid-Visual-Foundation-v1.zip
  git diff --check
  ./gradlew assembleDebug
  cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/BreezyCheatStation.apk

If build fails, share the exact e: Kotlin lines. Roll back using the tar snapshot.
