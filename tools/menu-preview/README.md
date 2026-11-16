# Menu preview prototype

On Windows, run `powershell -NoProfile -File tools/menu-preview/compose.ps1` from the repository root. It writes an eight-frame montage to `tools/menu-preview/output/cycleright_hats.png` using the bundled art. Output is ignored by Git.

This is a design prototype for checking card order and clipping. The production implementation is `MenuStripGenerator.kt`, which also handles editable assets, filtering stray export lines, and both directions. Check the actual menu in Minecraft before releasing an art change.
