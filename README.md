# Accessibility Inspector

[![Donate with PayPal](https://www.paypalobjects.com/en_US/i/btn/btn_donateCC_LG.gif)](https://www.paypal.com/donate/?business=E8XEQ2BQ5T4QU&no_recurring=0&currency_code=USD)

Be very careful with this service on a device that contains personal information. This service exposes the accessibility node tree through a web socket connection which contains all information visible on the screen. When you are not using this service, disable it from the Accessibility settings. 
## What does it do?
This project is a service that exposes the accessibility tree through json passed over a web socket connection. For visualizing the accessibility tree through a nested list view, check out the [Accessibility Inspector App](https://github.com/jwlilly/Accessibility-Inspector-App) that is built using Electron and Angular. 

## How to connect
The app is a web socket service that is enabled when the accessibility service is enabled through the Android accessibility settings menu. The port is currently hard coded to 38301. The [Browser WebSocket Client Chrome extension](https://chrome.google.com/webstore/detail/browser-websocket-client/mdmlhchldhfnfnkfmljgeinlffmdgkjo) is good for testing out the connection.
I recommend setting up ADB forward and connecting your device directly to your computer with USB debugging enabled. Using `adb forward tcp:38301 tcp:38301` to forward port 38301 to your computer provides a stable TCP connection through the USB (or VM if you are using an Android VM) that you can access using `127.0.0.1:38301`.

## Commands
Every message the service sends is a JSON object with a `type` of `tree`, `announcement`, `version`, `pong` or `error`.

``` json
{"message":"capture"}
```
Triggers a capture of the accessibility tree (`{"type":"tree","children":[...]}`) with _most_ of the available properties for each accessibility node, including the API 33-36 additions (required, tri-state checked, expanded state, supplemental description, container title, and more) when the device supports them. `{"message":"captureNotImportant"}` also includes views that are not important for accessibility. If the accessibility service isn't enabled, the reply is `{"type":"error","error":"..."}`.

``` json
{"message":"version"}
```
Returns `{"type":"version","versionCode":3,"versionName":"1.3.0","protocolVersion":2,"sdkInt":36}`. The inspector app uses this to tell users when the companion app needs to be updated.

``` json
{"message":"ping"}
```
The device responds with `{"type":"pong","message":"pong"}`. Useful for debugging the connection or keeping the connection alive for extended periods. 

The service will also automatically send any announcements triggered using [`View.announceForAccessibility()`](https://developer.android.com/reference/android/view/View#announceForAccessibility(java.lang.CharSequence)) in the form of `{"type":"announcement","announcement":[message]}`.

## Known issues
The web socket server is a little bit buggy. Future versions of this will rely on a websocket server running on the computer with the device simply acting as a web socket client. Sometimes the web socket server will stop responding and the process will not be killed when the accessibility service is killed. If this happens, you can try killing the service with `adb kill [service]` or rebooting the device. 

If the screen is actively updating when a capture is triggered, it may crash due to a null pointer exception. 

The code is very messy and is currently in a state of "is this possible and can I get it to work". Future iterations will focus on cleaning up the code, changing to a web socket client on the device rather than a server, stability, and efficiency improvements
