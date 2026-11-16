# Credits

PureTV is built on the method and the community data of two MIT-licensed projects:

- **cleanyourtv** by Gathin23 and contributors — https://github.com/Gathin23/cleanyourtv
  The approach (network ADB, `pm disable-user`, a recovery record, the launcher handover with
  rollback), the safety guards in `app/core.py` (core-package block, Home guard, input-driver
  guard, Settings/input handler guard, setup-wizard Wi-Fi guard), and the package statistics in
  `app/knowledge.json` (generated from its `examples/*/debloat*.txt` lists by
  `tools/build_knowledge.py`) all come from that project.
- **couch-tv** by Seagey — https://github.com/Seagey/couch-tv
  Reference for how a TV home screen asks for and holds the Home role.

MIT License — Copyright (c) 2026 Gathin23 and cleanyourtv contributors; Copyright (c) 2026 Seagey.
Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions: The above copyright notice and this
permission notice shall be included in all copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.

## TV demo assets (`app/ui/demo/`)

- `scenes/`: the five couch-tv scenes (photo, cut-out foreground, glass copy). couch-tv's credits say
  they were generated for that project from text only and may be used however you like.
- `logos/`: streaming app logos copied from couch-tv's demo (`remote/src/app-logos/`). They belong to
  their owners and are here only to show the demo; on a real TV the launcher shows each app's own icon.
- The setup's package list and its order (back up, ask what you use, disable in small batches, read the
  TV back, then hand over Home) come from cleanyourtv's `PROMPT.md` and
  `examples/acer-ar32hdigu2841at/debloat.txt`.
- `fonts/`: Antonio (clock) and Inter (interface), both under the SIL Open Font License.
- The glass clock technique (the scene's blurred copy clipped to the digits, scenery cut out in front)
  follows couch-tv's TV home.

- **tvOS Launcher** by techgenius (Apache License 2.0) — https://github.com/techgenius1/tvOS-Launcher
  The demo's Recent Apps switcher, the extra Control Center tiles (Bluetooth, Memory, weather), the
  hold-OK app menu, hidden apps and UI sounds follow features of that launcher. No code, sounds or
  images were copied from it; its "Apple TV" sound files were deliberately not used.
- Apple's tvOS design resources were not downloaded or used: their licence covers apps for Apple
  platforms only. The tvOS-style look here is drawn from scratch.

The PureTV app UI and the PureTV Home launcher are original to this project.

## Design

The TV design is inspired by Apple tvOS. Apple and tvOS are trademarks of Apple Inc. PureTV is an
independent project and is not affiliated with, endorsed by or sponsored by Apple. No Apple artwork, fonts
or sound files are used; the interface uses the Inter typeface (SIL Open Font License).
