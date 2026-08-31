# Daylog

Android app that records where you go all day in the background, groups the GPS
readings into the places you actually stopped, lets you name each place, and
saves the day as a real Excel file.

- Background tracking survives screen-off and app switching (foreground service)
- A stop = 3 minutes or more inside a 70 m circle; anything shorter is travel
- All times are 24-hour
- Name a place once and Daylog uses that name every time you go back
- Export writes a real `.xlsx` to `Downloads/Daylog/`
- Everything stays on the phone. No account, no server

Columns: Date, Time in, Place, Time out, Stay, Distance km, Latitude, Longitude

---

## Build the APK without a PC

This repo builds itself on GitHub. You do not need Android Studio.

1. Create a new repository on GitHub, for example `daylog`.
2. Upload every file and folder from this project into it, keeping the folder
   structure exactly as it is. On the GitHub website: **Add file → Upload files**,
   then drag the whole project folder in.
3. Go to the **Actions** tab. If it asks, click **I understand my workflows,
   enable them**.
4. The build starts on its own. If it does not, open **Build APK** on the left
   and click **Run workflow**.
5. Wait about 5 minutes. Open the finished run and download the
   **Daylog-apk** artifact at the bottom.
6. Unzip it on your phone and open `app-debug.apk` to install. Android will ask
   you to allow installing from unknown sources — allow it.

## First run

1. Open Daylog and turn the switch on.
2. Allow location. When Android asks again, choose **Allow all the time** —
   without this the phone stops the recording when the screen goes off.
3. Allow notifications. The ongoing notification is what keeps the service alive.
4. On Samsung, Xiaomi, Oppo and Huawei phones, also go to
   Settings → Apps → Daylog → Battery and set it to **Unrestricted**, otherwise
   the system kills the app after a few hours.

## Using it

Leave it running. Tap any stop in the list to give it a name. At the end of the
day press **Save today to Excel**, or **Save full history** for every day at once.
The file lands in `Downloads/Daylog/` and opens in Excel, Google Sheets or WPS.

## Settings you may want to change

In `app/src/main/java/com/gssc/daylog/Model.kt`:

| Value | Meaning | Default |
|---|---|---|
| `CLUSTER_M` | how wide one stop is, in metres | 70 |
| `MIN_MS` | how long you must stay for it to count | 3 minutes |

In `TrackerService.kt` the GPS interval is 60 seconds. Making it shorter gives
finer detail and uses more battery.
