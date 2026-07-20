## Metallum
Metallum is an experimental rendering backend for Minecraft on macOS and iOS that uses Apple's Metal API instead of OpenGL/Vulkan. It provides a more native rendering path and aims to improve performance and efficiency on Apple Silicon and iOS devices.

On macOS, the Metal rendering backend is loaded directly via the native bridge. On iOS, the precompiled iOS dylib (`libmetallum.dylib`, arm64, target iOS 14.0+) is included in the jar and intended to be consumed by [Amethyst-iOS](https://github.com/AngelAuraMC/Amethyst-iOS) .

This project is still experimental. Performance, stability, and compatibility may vary depending on your system and installed mods. If you encounter bugs, please report them on GitHub.

Compatible with Sodium.

vibecoded as hell

## Requirements

### macOS
- macOS
- Apple Silicon (M1 or newer)

### iOS
- iOS 14.0 or later
- An iPhone, iPad, or iPod touch capable of running [Amethyst-iOS](https://github.com/AngelAuraMC/Amethyst-iOS)
- Amethyst-iOS installed via TrollStore, AltStore, SideStore, or a jailbreak

## iOS Installation

Metallum is packaged as a Fabric mod and is loaded automatically on Amethyst-iOS when installed as a mod. The iOS native library (`libmetallum.dylib`, arm64) is bundled inside the jar and is loaded by the launcher at runtime via the embedded native bridge.

### Steps

1. **Install Amethyst-iOS** on your device following the [official instructions](https://github.com/AngelAuraMC/Amethyst-iOS). The recommended approach is to use TrollStore for permanent signing and automatic JIT.

2. **Download the latest Metallum jar** from the [Releases](https://github.com/Luna-QwQ/metallum/releases) page.

3. **Place the jar** in the `mods/` folder of your Minecraft instance on Amethyst-iOS (typically located at `~/Documents/minecraft/mods/` or accessible via the Amethyst file manager).

4. **Launch Minecraft** through Amethyst-iOS. Metallum will automatically detect the Metal backend and use it instead of the default OpenGL/Vulkan renderer.

### Notes

- The iOS dylib is built with target `arm64-apple-ios14.0` and is **unsigned**. It must be loaded from within the Amethyst app bundle's `Frameworks/` directory, where it is signed with the application's signing identity. This is handled automatically by the Amethyst packaging process.
- If you encounter crashes or rendering issues, try disabling other rendering-related mods first.
- Metallum requires Fabric Loader; make sure your Amethyst instance is using Fabric.

