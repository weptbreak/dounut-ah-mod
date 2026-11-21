# Donut AH Profit (Fabric, Minecraft 1.21.11)

Client-side, read-only overlay. Open the auction house and browse pages; hover any listing to see:
Worth (median unit price of listings you've seen x stack size), Resell at (worth minus your undercut),
and Profit (resell minus fee minus the listed price).

## Build
1. Install JDK 21.
2. Easiest: generate a fresh template at https://fabricmc.net/develop (1.21.11) so you get the Gradle wrapper,
   then copy `src/`, `build.gradle`, `gradle.properties`, `settings.gradle` over it.
   Or run `gradle wrapper --gradle-version 9.2.1` here if you have Gradle installed.
3. `./gradlew build` -> jar in `build/libs/` (use the one without `-sources`).
4. Put the jar + Fabric API 1.21.11 in your mods folder.

## Commands
/ahp fee <percent>        AH sell fee (default 0 - set the real one)
/ahp undercut <percent>   how far under market to list (default 1)
/ahp keyword <text>       AH screen title match, comma separated (default "auction")
/ahp any <true|false>     treat every chest screen as the AH
/ahp debug                shows screen title + parse result in tooltips
/ahp clear                wipe price history
/ahp status
