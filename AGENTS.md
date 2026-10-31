# Project contribution rules

- Keep Jetpack Compose UI in `ui/` and app behavior, recommendation logic, persistence, networking, and playback rules in the appropriate `viewmodel/`, `data/`, or `service/` layers. UI composables should render state and forward user actions rather than own business rules.
- Keep every source file below 500 lines. Split files by responsibility before a change would bring a file to 500 lines.
- Keep related UI and behavior in clearly named, focused files; avoid growing screen files into mixed UI and logic modules.
