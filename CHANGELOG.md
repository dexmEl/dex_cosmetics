# Changelog

## Unreleased

- Centralize cosmetic actions and validate ownership when an action occurs, including skins selected from an already-open menu.
- Preserve skins on ground leftovers after failed or partial inventory pickups when sharing is disabled.
- Refresh renderers on revocation and reload; clear parked back cosmetics and scheduled work during lifecycle cleanup.
- Commit SQLite changes before changing cached state; make revocation transactional and surface write failures.
- Stage configuration reloads, reject malformed/duplicate definitions, preserve the previous state/pack on build failure, and report command failures accurately.
- Split configuration parsing, validation, paths, defaults, model construction, and color conversion into focused components.
- Use Polymer's public model creator and retain model allocations across reloads. Configure autohost before startup and prevent concurrent startup pack builds.
- Enforce the built-in particle style budget. Remove the unused autosave and pack acceptance/message fields; document migration and restart requirements.
- Generate the bundled asset index, including animation metadata, and remove unused missing GUI icon registrations.
- Align metadata with the existing CC0 license and correct repository/dependency documentation.
- Add regression tests, formatting checks, Java 21 CI, pinned Loom, and a portable preview tool. Untrack local compiler logs and generated previews.
