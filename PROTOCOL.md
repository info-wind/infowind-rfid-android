<p align="center"><a href="https://info-wind.ru/"><img width="70" src="./infowind.svg"></a><br>InfoWind</p>

# AXELOT PDT Scanner protocol description

> Protocol Version: 1.8.0

## Protocol

The protocol is inspired by the Hayes command set (AT command set) commonly seen in small serial devices. General information can be found at https://en.wikipedia.org/wiki/Hayes_command_set

The query, or params part of the commands that go after `?` are standart URL query paramers, their order does not matter, only their presence. More info can be found at https://en.wikipedia.org/wiki/Query_string

***The protocol is to be implemented as asynchronous***, there should be no expectation that the responses to the commands will follow immedeatly after the commands were sent, because there might be other continuous commands running in that period that would send their data before the current command is recievd and parsed. `+HB\n` heartbeat messages also come between any commands that are running and should be handled apropriety by the side waiting for a command response.

## Heartbeat: `+HB\n` - every 1 second

Every 1 second the module send a heartbeat command `+HB\n`.

These messages are sent by an internal timer without regard for any command or even connection state between the connected systems.

Please note that as `+HB\n` heartbeat messages run on an internal timer and do not wait for any connection to be established it is possible to resieve a full or even a partial `+HB\n` message right after connecting to the module before sending any `AT` commands. The system implementing this protocol must thus be resistant to receiving such partial message noise. A state machine based on the `switch` statment combined with a state contaier is advisable for implementation.

Example for Heartbeat messages in the protocol log (note the `B\n` in the beginning the is partial of the `+HB\n` that was being sent as the connection was being established, also note the differences in when the `+HB` is received in regards to the `AT+SCAN` and `AT+INTERRUPT` request and responses, it can be recieved between any response lines as it is running asynchronously), read on commands bellow for more:

```log
B
+HB
+HB
AT
+HB
OK
+HB
AT+SCAN?COUNT=3&DURATION=3000
+HB
1,300833B2DDD9014000000032,1,-57.6,0,0,1
+HB
+HB
NOTFOUND
NOTFOUND
OK
+HB
+HB
AT+SCAN
OK
+HB
+HB
+HB
AT+INTERRUPT
+HB
INTERRUPTED AT+SCAN
OK
AT+SCAN
+HB
OK
+HB
+HB
AT+INTERRUPT
INTERRUPTED AT+SCAN
+HB
OK

```

## Commands

**Each command must be followed by the standard line ending `\n` OR `\r\n`.**

Heres a shot list of supported commands:
- `AT`
- `AT+VERSION`
- `AT+INTERRUPT`
- `AT+SCAN`
- `AT+FIND`
- `AT+SYNC`
- `AT+PREFS`
- `AT+REBOOT`
- `AT+DOWNLOAD`
- `AT+CLEAR`

Also the following commands are deprecated:
- `AT+SCAN?\r\n`
- `AT+SCAN=0\r\n`
- `AT+SCAN=1\r\n`

## Command: `AT\n` - generic handshake, check if device is present
```
AT
```
Response: `OK\n` - scanner healthy and ready
```
AT
OK
```

## Command: `AT+VERSION\n` - get device's protocol version
```
AT+VERSION
```
The response is:
- protocol version in format of `<major>.<minor>.<patch>` followed by the standard line ending `\n`
- the end of command `OK\n`.

```
AT+VERSION
1.7.0
OK
```

## Command: `AT+SYNC,<remote_time>\n` - sync time between devices
```
AT+SYNC,1706532328203
```
The response is:
- a key word `SYNC`
- a single comma `,`
- the remote time from the command paramenters
- a single comma `,`
- the current device time in milliseconds
- the standard line ending `\n`.

```
AT+SYNC,1706532328203
SYNC,1706532328203,40295
```

## Command: `AT+PREFS<params...>\n` - set or reset preferences
```
AT+PREFS?ssid=Device_wifi&pass=12345678
```

Currently supported params are:
- `ssid` - wifi hotspot and bluetooth device name
- `pass` - wifi hotspot password
- `reset` - reset all prefs to defult values

The response is:
- the end of command `OK\n`.

```
AT+PREFS?ssid=Device_wifi&pass=12345678
OK

AT+PREFS?reset
OK
```

## Command: `AT+REBOOT\n` - reboot software
```
AT+REBOOT
```
No response, the device reboots. The heartbeat will go missing for a few bits while device reboots, any established connections will be lost. Mostly for debug.

## Command: `AT+DOWNLOAD\n` - download stored labels
```
AT+DOWNLOAD
```
The response is:
- a key word `DOWNLOAD`
- a single comma `,`
- count of stored labels
- the standard line ending `\n`
- stored labels each on its own line
- the end of command `OK\n`.

```
AT+DOWNLOAD
DOWNLOAD,3
1,300833B2DDD9014000000032,1,-57.6,0,0,1
2,300833B2DDD9014000000033,1,-58.1,0,0,1
3,300833B2DDD9014000000034,1,-52.3,0,0,1
OK

```
## Command: `AT+CLEAR\n` - clear stored labels from memory
```
AT+CLEAR
```
The response is:
- the end of command `OK\n`.

```
AT+CLEAR
OK
```

## Command: `AT+INTERRUPT\n` - interrupt a currently running command
> **Note**  
> Its recommended to use this instead of the deprecated ~~`AT+SCAN=0\n`~~, it is more explicit about what it does, additionaly providing info about what command was interrupted if any
```
AT+INTERRUPT
```

If a **command was running**, the response is:
- a key word `INTERRUPTED`
- a single white space
- a signature of the interrupted command followed by the standard line ending `\n`
- the end of command `OK\n`.
```
AT+INTERRUPT
INTERRUPTED AT+SCAN?COUNT=inf
OK
```

If **no command was running**, the response is:
- a key word `INTERRUPTED`
- a single white space
- a key word `NOTHING` followed by the standard line ending `\n`
- the end of command `OK\n`.
```
AT+INTERRUPT
INTERRUPTED NOTHING
OK
```

## Command: `AT+SCAN<?params...>\n` - request a scan with the given parameters
```
AT+SCAN
```

The `<params...>` can be:
- `COUNT` (default `1`) the amount of labels to scan and return back
- `DURATION` (default `inf`) the amount of milliseconds to scan for

Each parameter can be a number or an `inf` meaning infinite.

|`COUNT`|`DURATION`|Meaning
|-|-|-|
|n|n|Wait to scan `n` labes for `n` milliseconds|
|n|inf|Wait to scan `n` labes infinetly|
|inf|n|Scan any amount of labels within `n` milliseconds|
|inf|inf|Scan all labels until explicitly interrupted|

Given the default values the follwoing short versions can be used:
- `AT+SCAN` wait indefinitly for and scan 1 label
- `AT+SCAN?COUNT=inf` enable continuous scanning, same as the deprecated ~~`AT+SCAN=1`~~

When a commands runs infintely, the labels' data is sent immedetely after it is scanned, there can be 2 types of responses to this command:

If the command **will run infinetly** (both `COUNT` and `DURATION` are `inf`), the response is:

- the end of command `OK\n`, to acknowledge the succes of the command
- infinite scanned labels' data according to <a href="#label-data-format">label data format</a>, each followed by the standard line ending `\n`.
```
AT+SCAN?COUNT=inf
OK
1,300833B2DDD9014000000032,1,-57.6,0,0,1
2,300833B2DDD9014000000033,1,-58.1,0,0,1
3,300833B2DDD9014000000034,1,-52.3,0,0,1
```

If the command **will not run infinetly**, the response is:

- multiple scanned label info according to <a href="#label-data-format">label data format</a> or a <a href="#notfound">`NOTFOUND`</a> keyword, each followed by the standard line ending `\n`
- the end of command `OK\n` to acknowledge successful scan end.
```
AT+SCAN?COUNT=3
1,300833B2DDD9014000000032,1,-57.6,0,0,1
2,300833B2DDD9014000000033,1,-58.1,0,0,1
3,300833B2DDD9014000000034,1,-52.3,0,0,1
OK
```

## Command: `AT+FIND?<filter&params...>\n` - search for labes using a filter with parameters
```
AT+FIND?PERSISTENT&COUNT=2&DURATION=20000
```

The `filter` can be:
- `BEST` find the labes with best RSSI, sort by it and send the best first
- `PERSISTENT` find the labes that are the most persistent or stable, to be used on a mooving scanner to detect the labes that are moving with it

The `<params...>` must be numbers, no `inf` or missing values allowed:
- `COUNT` (no default) the amount of labels to scan and return back
- `DURATION` (no default) the amount of milliseconds to scan for

The response is:

- multiple scanned label info according to <a href="#labelq-data-format">label with quality/confidence data format</a> or a <a href="#notfound">`NOTFOUND`</a> keyword, each followed by the standard line ending `\n`
- the end of command `OK\n` to acknowledge successful scan end.
```
AT+FIND?PERSISTENT&COUNT=4&DURATION=20000
1,300833B2DDD9014000000032,1,-57.6,0,0,1,0.988
2,300833B2DDD9014000000033,1,-58.1,0,0,1,0.873
3,300833B2DDD9014000000034,1,-52.3,0,0,1,0.456
NOTFOUND
OK
```

## Errors

If a command encounters an error during execution, such as recieving incorrect parameters, an error is thrown and the command completes with `OK`.

The error syntax is a follows:

- a key word `ERROR`
- a single white space
- a string describing the error followed by the standard line ending `\n`
- the end of command `OK\n`.

For example:
```
AT+FIND
ERROR Filter not provided
OK

AT+FIND?PERSISTENT
ERROR Count not provided
OK

AT+FIND?PERSISTENT?COUNT=inf
ERROR Count can not be inf
OK
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



## <span id="notfound">`NOTFOUND` keyword</span>
If a `COUNT` is requested, and more labes are scanned, the latter labes are dismissed. If a `DURATION` is also provided and by the time it ends less labes are scanned then requested the missing labesl are represented in a response by a `NOTFOUND` keyword:
```
AT+SCAN?COUNT=3&DURATION=1000
1,300833B2DDD9014000000032,1,-57.6,0,0,1
NOTFOUND
NOTFOUND
OK
```

## Deprecated command: ~~`AT+SCAN=1\n`~~ - enable continuous scanning
> **Warning**  
> Deprecated since 1.4.0, use `AT+SCAN?COUNT=inf` instead

```
AT+SCAN=1
```
Response: `OK\n` - continous scanning enabled
```
AT+SCAN=1
OK
```
After this the scanner starts sending label data on each line followed by the standard line ending `\n` in the <a href="#label-data-format">label data format</a> outlined below.
```
AT+SCAN=1
OK
1,300833B2DDD9014000000032,1,-57.6,0,0,1
2,300833B2DDD9014000000033,1,-58.1,0,0,1
3,300833B2DDD9014000000034,1,-52.3,0,0,1
4,300833B2DDD9014000000035,1,-55.8,0,0,1
5,300833B2DDD9014000000036,1,-51.4,0,0,1
...
```

## Deprecated command: ~~`AT+SCAN=0\n`~~ - stop continuos scanning / interrupt active scanning command
> **Warning**  
> Deprecated since 1.4.0, use `AT+INTERRUPT` instead

```
AT+SCAN=0
```
Response: `OK\n` - continuos scanning disbaled or an active scanning command interrupted
```
AT+SCAN=0
OK
```
## Deprecated command: ~~`AT+SCAN?`~~ - request scan of a single label
> **Warning**  
> Deprecated since 1.4.0, use simple `AT+SCAN` instead  
> For now, if the request wording is exactly `AT+SCAN?\n` an empty line is added after the scan result, to support backwards compatability, **this will be removed in future versions, do not rely on this!**

```
AT+SCAN?
``` 
Response: `1,300833B2DDD9014000000032,1,-57.6,0,0,1\n\nOK\n`  
The response is:
- scanned label info according to <a href="#label-data-format">label data format</a> followed by the standard line ending `\n`
- an empty line `\n`
- the end of command `OK\n`.

```
AT+SCAN?
1,300833B2DDD9014000000032,1,-57.6,0,0,1

OK
```
