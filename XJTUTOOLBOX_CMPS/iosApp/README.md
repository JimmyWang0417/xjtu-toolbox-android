# iOS Host

This folder contains the SwiftUI host for the shared Compose Multiplatform UI.

The app entry is:

```swift
ContentView()
```

`ContentView` embeds:

```swift
MainViewControllerKt.MainViewController()
```

Generate or open the Xcode project from a Compose Multiplatform IDE template, then point the shared framework integration to `:shared`.
