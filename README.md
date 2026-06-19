# TIA Project

TIA Project is an accessible workout built with a phone app and a Wear OS app.
The phone handles setup, progress history, and spoken navigation.
The watch handles live tracking, session controls, and in-session coaching.

## Modules

- `app`: Android phone app written with Jetpack Compose.
- `wear`: Wear OS companion app that reads sensors and runs the live workout session.

## Phone App Flow

The phone entry point is [`MainActivity.kt`].
It keeps navigation inside Compose and moves the user through:

1. `MenuScreen`
2. Goal type selection
3. Goal value selection
4. Difficulty selection
5. Session summary
6. `TrainingSession`
7. `SessionSummaryScreen`

The phone also owns the main user preferences:

- `voiceoverEnabled`
- `musicEnabled`
- `vibrationEnabled`
- `darkModeEnabled`
- `has_seen_guide`

## Watch Communication

The phone sends session metadata to the watch from [`TrainingSession.kt`].
The main message paths are:

- `/session_start`: starts a workout on the watch and sends the goal, difficulty, and personal-best context.
- `/watch_command`: syncs pause and resume actions between phone and watch.
- `/session_end`: tells the watch to stop the active session.

The phone receives live updates from the watch in [`PhoneWearListenerService.kt`], then stores them in [`WatchDataRepository.kt`] so Compose screens can react to them.

## Wear App Flow

The watch session lifecycle is split between two files:

- [`WearListenerService.kt`]: receives phone messages and updates shared watch session state.
- [`MainActivity.kt`]: reads sensors, estimates distance and cadence, updates progress, and drives spoken and vibration feedback.

During a session, the watch:

1. Resets the previous sensor state.
2. Starts listening to the step counter and accelerometer.
3. Estimates distance using a stride length that changes with cadence.
4. Updates progress and stage information.
5. Sends stage changes and final results back to the phone.

The live UI is [`WatchProgressScreen.kt`].
It provides pause, resume, and finish gestures and reflects warning states like `needsSpeedUp` and `isStopped`.

## Session Modes

- `JUST VIBING`: calm session, no progress coaching during the workout.
- `STARTING TO SWEAT`: stage-based progress feedback with halfway and end-of-stage cues.
- `PUSHING LIMITS`: stage feedback plus personal-best coaching.

## Persistence

Completed sessions are stored in SharedPreferences under `training_sessions`.
Each entry is serialized as:

`timestamp|distanceKm|elapsedSeconds|mode`

`mode` is normalized by goal type and goal value, for example:

- `1 KM`
- `1 MIN`
- `5 MIN`

Personal bests are derived from these saved sessions in [`TrainingSession.kt`]:

- Distance goals use the fastest completion time.
- Time goals use the greatest distance.

