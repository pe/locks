# Format login / lock messages

Read logs of macOS locks/unlocks. Group and format them for copy & paste into a time tracking spreadsheet.

```console
log show --predicate '(subsystem == "com.apple.login") && (category == "Logind_General") && (eventMessage beginswith "-[SessionAgent SA_SetSessionStateForUser:state:reply:]:536: state set to:")' --style compact --last 2d
```
