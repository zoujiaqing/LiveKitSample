# LiveKit Android

## Introduction
- Implementation of LiveKit SDK.<br>
- Helpful comments added<br>
- Details for setting up local server for generating tokens.

## LiveKit Credentials
Add your own LiveKit url and token bofore running the app.

## Setting up Local LiveKit Server (Mac)
### Create Local Server: ([LiveKit](https://docs.livekit.io/realtime/self-hosting/local/#Install-LiveKit-Server)) 
On CLI run "livekit-server" (for only local ip access) or "livekit-server --dev --bind 0.0.0.0" (for global ip access)

Open this on browser: "http://127.0.0.1:7880/ " it should show "Ok" message so server is up and running

### Generate Tokens: ([LiveKit](https://docs.livekit.io/realtime/server/generating-tokens/))
Open project folder in VS Code: if npm packages not installed (run in VSCode cli: "npm install") and than run "node server.js" -> This will start a server to generate tokens

Open Postman, do a GET request to url: "localhost:3333/getToken/applicationNameOfYourChoice" e.g. localhost:3333/getToken/TestingLiveKit -> in result it will give out LiveKit token

### Test Connection: ([LiveKit](https://livekit.io/connection-test))
Test local connection with using token got from step 3 and url as: "ws://localhost:7880" (use localhost if same machine otherwise use your ip address instead)

### Connection to Room: ([LiveKit](https://docs.livekit.io/realtime/client/connect/) )
Test using 2 participants, 1 sharing screen and other displaying the video stream.
