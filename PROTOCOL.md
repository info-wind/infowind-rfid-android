<p align="center"><a href="https://info-wind.ru/"><img width="70" src="./infowind.svg"></a><br>InfoWind</p>

# Scanner protocol description

> Protocol Version: 2.0.0

## Protocol

The protocol is based around a slimmed fown version of the HTTP protocol. All commands are valid URLs, and the responses can be of any format. The query, or params part of the commands that go after `?` are standart URL query paramers, their order does not matter, only their presence. More info can be found at https://en.wikipedia.org/wiki/Query_string

***The protocol is to be implemented as asynchronous***, there should be no expectation that the responses to the commands will follow immedeatly after the commands were sent, because there might be other continuous commands running in that period that would send their data before the current command is recievd and parsed. `+hb` heartbeat messages also come between any commands that are running and should be handled apropriety by the side waiting for a command response.

## Heartbeat: `+hb` - every 1 second

Every 1 second the module sends a heartbeat message `+hb`.

These messages are sent by an internal timer without regard for any command or even connection state between the connected systems.

Please note that as `+hb` heartbeat messages run on an internal timer and do not wait for any connection to be established it is possible to recieve a full or even a partial `+hb` message right after connecting to the module before sending any commands. The system implementing this protocol must thus be resistant to receiving such partial message noise. A state machine based on the `switch` statment combined with a state contaier is advisable for implementation.

Example for Heartbeat messages in the protocol log (note the `b` in the beginning the is partial of the `+hb` that was being sent as the connection was being established, also note the differences in when the `+hb` is received in regards to the `/scan` and `/interrupt` request and responses, it can be recieved between any response lines as it is running asynchronously), read on commands bellow for more:

```log
b
+hb
+hb
/at
+hb
ok
+hb
/scan?count=3&duration=3000
+hb
1,300833B2DDD9014000000032,1,-57.6,0,0,1
+hb
+hb
notfound
notfound
ok
+hb
+hb
/scan
ok
+hb
+hb
+hb
/interrupt
+hb
interrupted /scan
/scan
+hb
ok
+hb
+hb
/interrupt
interrupted /scan
+hb

```

## Commands

**Each command must be followed by the line ending synbol `\n`.**

Heres a shot list of supported commands:
- `/at`
- `/version`
- `/interrupt`
- `/scan`
- `/find`
- `/sync`
- `/prefs`
- `/reboot`
- `/download`
- `/clear`

</details>

## Command: `/at` - generic handshake, check if device is present
```
/at
```
Response: `ok` - scanner healthy and ready
```
/at
ok
```

## Command: `/version` - get device's protocol version
```
/version
```
The response is:
- protocol version in format of `<major>.<minor>.<patch>`

```
/version
1.7.0
```

## Command: `/sync,<remote_time>` - sync time between devices
```
/sync,1706532328203
```
The response is:
- a key word `sync`
- a single comma `,`
- the remote time from the command paramenters
- a single comma `,`
- the current device time in milliseconds

```
/sync,1706532328203
sync,1706532328203,40295
```

## Command: `/prefs<params...>` - set or reset preferences
```
/prefs?ssid=device_wifi&pass=12345678
```

Currently supported params are:
- `ssid` - wifi hotspot and bluetooth device name
- `pass` - wifi hotspot password
- `reset` - reset all prefs to defult values

The response is:
- the end of command `ok`.

```
/prefs?ssid=device_wifi&pass=12345678
ok

/prefs?reset
ok
```

## Command: `/reboot` - reboot software
```
/reboot
```
No response, the device reboots. The heartbeat will go missing for a few bits while device reboots, any established connections will be lost. Mostly for debug.

## Command: `/download` - download stored labels
```
/download
```
The response is:
- a key word `download`
- a single comma `,`
- count of stored labels
- stored labels each on its own line
- the end of command `ok`.

```
/download
download,3
1,300833B2DDD9014000000032,1,-57.6,0,0,1
2,300833B2DDD9014000000033,1,-58.1,0,0,1
3,300833B2DDD9014000000034,1,-52.3,0,0,1
ok

```
## Command: `/clear` - clear stored labels from memory
```
/clear
```
The response is:
- the end of command `ok`.

```
/clear
ok
```

## Command: `/interrupt` - interrupt a currently running command
```
/interrupt
```

If a **command was running**, the response is:
- a key word `interrupted`
- a single white space
- a signature of the interrupted command
```
/interrupt
interrupted /scan?count=inf
```

If **no command was running**, the response is:
- a key word `interrupted`
- a single white space
- a key word `nothing` 
```
/interrupt
interrupted nothing
```

## Command: `/scan<?params...>` - request a scan with the given parameters
```
/scan
```

The `<params...>` can be:
- `count` (default `1`) the amount of labels to scan and return back
- `duration` (default `inf`) the amount of milliseconds to scan for

Each parameter can be a number or an `inf` meaning infinite.

|`count`|`duration`|Meaning
|-|-|-|
|n|n|Wait to scan `n` labes for `n` milliseconds|
|n|inf|Wait to scan `n` labes infinetly|
|inf|n|Scan any amount of labels within `n` milliseconds|
|inf|inf|Scan all labels until explicitly interrupted|

Given the default values the follwoing short versions can be used:
- `/scan` wait indefinitly for and scan 1 label
- `/scan?count=inf` enable continuous scanning

When a commands runs infintely, the labels' data is sent immedetely after it is scanned, there can be 2 types of responses to this command:

If the command **will run infinetly** (both `count` and `duration` are `inf`), the response is:

- the end of command `ok`, to acknowledge the succes of the command
- infinite scanned labels' data according to <a href="#label-data-format">label data format</a>, each .
```
/scan?count=inf
ok
1,300833B2DDD9014000000032,1,-57.6,0,0,1
2,300833B2DDD9014000000033,1,-58.1,0,0,1
3,300833B2DDD9014000000034,1,-52.3,0,0,1
```

If the command **will not run infinetly**, the response is:

- multiple scanned labels info according to <a href="#label-data-format">label data format</a> or a <a href="#notfound">`notfound`</a> keyword 
- the end of command `ok` to acknowledge successful scan end.
```
/scan?count=3
1,300833B2DDD9014000000032,1,-57.6,0,0,1
2,300833B2DDD9014000000033,1,-58.1,0,0,1
3,300833B2DDD9014000000034,1,-52.3,0,0,1
ok
```

## Command: `/find?<filter>&<params...>` - search for labes using a filter with parameters
```
/find?persistent&count=2&duration=20000
```

The `filter` can be:
- `best` find the labes with best RSSI, sort by it and send the best first
- `persistent` find the labes that are the most persistent or stable, to be used on a moving scanner to detect the labes that are moving with it

The `<params...>` must be numbers, no `inf` or missing values allowed:
- `count` (no default) the amount of labels to scan and return back
- `duration` (no default) the amount of milliseconds to scan for

The response is:

- multiple scanned labels info according to <a href="#labelq-data-format">label with quality/confidence data format</a> or a <a href="#notfound">`notfound`</a> keyword 
- the end of command `ok` to acknowledge successful scan end.
```
/find?persistent&count=4&duration=20000
1,300833B2DDD9014000000032,1,-57.6,0,0,1,0.988
2,300833B2DDD9014000000033,1,-58.1,0,0,1,0.873
3,300833B2DDD9014000000034,1,-52.3,0,0,1,0.456
notfound
ok
```

## Errors

If a command encounters an error during execution, such as recieving incorrect parameters, an error is thrown.

The error syntax is a follows:

- a key word `error`
- a single white space
- a string describing the error

For example:
```
/find
error Filter not provided

/find?persistent
error Count not provided

/find?persistent?count=inf
error Count can not be inf
```

<h2 id="label-data-format">Label data format</h2>
Scan response consists of data fields are separated by a single comma:

```
1,300833B2DDD9014000000032,1,-57.6,0,0,1
```

Data fields are as follows:
- `1` Scan number in result sequence
- `300833B2DDD9014000000032` Electronic Product Code (EPC)
- `1` Antenna number
- `-57.6` Received signal strength indication (RSSI)
- `0` GPS longitude of scanner
- `0` GPS latitude of scanner
- `1` Tag type

<h2 id="labelq-data-format">Label with Quality/Confidence data format</h2>
When using search commands an additonal field is provided - quality or confidence level:

```
1,300833B2DDD9014000000032,1,-57.6,0,0,1,2.973
```

Data fields are as follows:
- `1` Scan number in result sequence
- `300833B2DDD9014000000032` Electronic Product Code (EPC)
- `1` Antenna number
- `-57.6` Received signal strength indication (RSSI)
- `0` GPS longitude of scanner
- `0` GPS latitude of scanner
- `1` Tag type
- `2.973` Quality or confidence level



## <span id="notfound">`notfound` keyword</span>
If a `count` is requested, and more labes are scanned, the latter labes are dismissed. If a `duration` is also provided and by the time it ends less labes are scanned then requested the missing labesl are represented in a response by a `notfound` keyword:
```
/scan?count=3&duration=1000
1,300833B2DDD9014000000032,1,-57.6,0,0,1
notfound
notfound
ok
```