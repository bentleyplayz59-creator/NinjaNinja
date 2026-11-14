# Mod Builder

Android app + GitHub Actions workflows that turn mod source into a mod ZIP, and DLLs into source.

## One-time setup
1. Make a PRIVATE GitHub repo and upload everything in this zip.
2. Nothing to download: the workflow uses `Pathoschild/SMAPI-ModBuildWorkflow/add-build-environment@v1`, which fetches the game/SMAPI reference assemblies automatically.
3. Actions tab -> "Build APK" -> run it -> download the `ModBuilder-apk` artifact, unzip, install the APK.
4. Make a GitHub token (fine-grained, this repo only: Contents read/write, Actions read).

## App identity
- Package name: `com.bentleyplayz.modbuilderapp`
- Icon: Bentley Playz "BP" logo generated with Pillow by `tools/make_icon.py` during the build.
- Signature: the workflow signs the APK with your own key if these repo secrets exist:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
  Without them it falls back to the default debug signature. Never commit the keystore file.

## Use
- Open the app, enter token + `owner/repo`.
- "Pick source ZIP and build mod": zip must contain your .csproj, manifest.json, assets.
  The app uploads it to `inbox/`, the "Build Mod" workflow compiles the DLL and zips the mod,
  and the app saves the mod ZIP to Downloads.
- "Pick DLL and decompile": uploads to `decompile/`, ILSpy decompiles it, and the app saves
  `<name>-source.zip` to Downloads.
- Uploaded files stay in `inbox/` and `decompile/`; delete old ones sometimes.

Nothing here has been run yet. Expect to adjust the workflow after the first real build.
