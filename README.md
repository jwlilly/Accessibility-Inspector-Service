# Accessibility Inspector
## What does it do?
This project is a service that exposes the accessibility tree through json passed over a web socket connection. For visualizing the accessibility tree through a nested list view, check out the [Accessibility Inspector App](https://github.com/jwlilly/Accessibility-Inspector-App) that is built using Electron and Angular. 

## How to connect
The app is a web socket service that is enabled when the accessibility service is enabled through the Android accessibility settings menu. The port is currently hard coded to 38301. The [Browser WebSocket Client Chrome extension](https://chrome.google.com/webstore/detail/browser-websocket-client/mdmlhchldhfnfnkfmljgeinlffmdgkjo) is good for testing out the connection.
I recommend setting up ADB forward and connecting your device directly to your computer with USB debugging enabled. Using `adb forward tcp:38301 tcp:38301` to forward port 38301 to your computer provides a stable TCP connection through the USB (or VM if you are using an Android VM) that you can access using `127.0.0.1:38301`.

## Commands
``` json
{"message":"capture"}
```
Triggers a capture of the accessibility tree and will return a json with a screenshot in base64 along with the accessibility tree and _most_ of the available properties for each accessibility node. 

``` json
{"message":"ping"}
```
The device responds with "pong". Useful for debugging the connection or keeping the connection alive for extended periods. 

The service will also automatically send any announcements triggered using [`View.announceForAccessibility()`](https://developer.android.com/reference/android/view/View#announceForAccessibility(java.lang.CharSequence)) in the form of `{"announcement":[message]}`.

## Known issues
The web socket server is a little bit buggy. Future versions of this will rely on a websocket server running on the computer with the device simply acting as a web socket client. Sometimes the web socket server will stop responding and the process will not be killed when the accessibility service is killed. If this happens, you can try killing the service with `adb kill [service]` or rebooting the device. 

If the screen is actively updating when a capture is triggered, it may crash due to a null pointer exception. 

The code is very messy and is currently in a state of "is this possible and can I get it to work". Future iterations will focus on cleaning up the code, changing to a web socket client on the device rather than a server, stability, and efficiency improvements
