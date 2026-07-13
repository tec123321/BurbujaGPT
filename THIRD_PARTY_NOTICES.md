# Third-party notices

## SuperWindow

Globo GPT V16 includes an independently adapted implementation based on architectural ideas and portions of the open-source project **SuperWindow** by eiyooooo.

- Source: https://github.com/eiyooooo/SuperWindow
- License: GNU General Public License v3.0
- Upstream revision reviewed: `9358ce75ae7e20eeea99e0e9a0ea92496eaa919a`

The adapted implementation is primarily contained in:

- `SystemDisplayEngine.java`
- `VirtualDisplaySessions.java`
- `ShizukuDisplayBridge.java`

Changes made for Globo GPT include conversion to Java/reflection-only wrappers, ChatGPT-specific task discovery, Android bubble integration, Samsung-oriented diagnostics, fallback launch paths and per-bubble lifecycle management.

This V16 branch is distributed as source alongside the APK build. There is no warranty.
