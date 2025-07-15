# Google Cloud Setup for TTS/STT

## Prerequisites

You need a Google Cloud service account JSON file with Text-to-Speech and Speech-to-Text API permissions.

## Setup Instructions

1. **Create or locate your Google Cloud service account JSON file**
   - The file should be named: `minecraftsever-463307-4aca77796027.json`
   - This is configured in `src/main/resources/config.yml`

2. **Place the credentials file in one of these locations**
   - Option 1: In the server's root directory (where you run the server from)
   - Option 2: In the plugin's data folder: `plugins/MinecraftAIBrain/`
   - Option 3: In the plugins folder where the JAR file is located: `plugins/`

3. **Verify the file path**
   - The config.yml expects the file at: `minecraftsever-463307-4aca77796027.json`
   - If your file has a different name, update the config.yml:
     ```yaml
     speech:
       credentials:
         path: "your-credentials-file.json"
     
     tts:
       credentials:
         path: "your-credentials-file.json"
     ```

4. **Enable the required APIs in Google Cloud Console**
   - Text-to-Speech API
   - Speech-to-Text API

## Common Issues

### "TTS 오류 : [text-to-speech] SERVICE_NOT_FOUND: TextToSpeech service is not running"

This error typically means:
1. The credentials file is not found at the specified path
2. The credentials file doesn't have the correct permissions
3. The Google Cloud APIs are not enabled

### Debugging Steps

1. Check server logs for specific error messages about credentials
2. Verify the file exists where the server expects it
3. Ensure the service account has the necessary permissions:
   - `roles/cloudtexttospeech.user` for TTS
   - `roles/cloudspeech.user` for STT

## Alternative: Using Environment Variables

Instead of a file path, you can set the environment variable:
```bash
export GOOGLE_APPLICATION_CREDENTIALS="/path/to/your/credentials.json"
```

Then start your Minecraft server.