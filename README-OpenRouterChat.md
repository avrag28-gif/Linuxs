# OpenRouter Chat Android

Native Android chat app for OpenRouter model `nvidia/nemotron-3.5-super-vl:free`.

## Get the APK
The GitHub Actions workflow builds a debug APK whenever changes are pushed to `main` or `master`.
1. Open the repository's Actions tab.
2. Select the latest **Build Android APK** run.
3. When completed, download the `OpenRouterChat-debug-apk` artifact.
4. Extract the downloaded ZIP and install `app-debug.apk` on Android.

## API key safety
The app prompts for an API key at runtime; no key is embedded in the repository. Because keys have been shared in chat, revoke those keys and create a new one. Never commit API keys to source control. For public distribution, use a backend proxy to protect your key.

## API
POST `https://openrouter.ai/api/v1/chat/completions`.
