# PureTV

If someone asks you to set up, clean, install PureTV on, or restore their TV, follow [PROMPT.md](PROMPT.md) step by step. Ask its questions as tap-to-answer choices, and change the TV only through `puretv.py`.

If they're working on the code instead:

- `app/core.py` is the TV engine.
- `puretv.py` is its command line.
- `launcher/` is the native Android Home screen. Build it with:
  ```bash
  python tools/build_native_assets.py
  ```
  ```bash
  python launcher/build.py
  ```
- `tests/test_core.py` runs end to end against a pretend TV.
