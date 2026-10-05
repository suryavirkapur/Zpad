<p align="center"><img src="docs/logo.png" width="128" alt="Zpad logo"></p>

<h1 align="center">Zpad</h1>

<p align="center">A small, quiet notepad for Android. Your notes are plain <code>.txt</code> files in <code>Downloads/Zpad</code>, yours to keep.</p>

## Features

- Plain-text notes stored as files you can open anywhere
- Fast search across titles and contents
- Pin notes to keep them on top; swipe to pin or delete (with undo)
- Find in note, undo/redo
- Share as text, file, or image
- Import existing `.txt` files
- Themes: Classic, Slate, Gruvbox, Nord, Sepia, each in light and dark
- Monospace, sans-serif, or serif text at an adjustable size

## Building

Requires Android Studio (or JDK 17+ and the Android SDK). The app targets Android 14+ (API 34).

```sh
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run unit tests
```

## Credits

Lexend font by the Lexend Project, licensed under the [SIL Open Font License](app/src/main/assets/licenses/Lexend-OFL.txt).
