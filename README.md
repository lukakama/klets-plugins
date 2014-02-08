KLets plugin pack
=============

This is the repository project for new and experimental commands for KLets, translated in Italian and English.

The project and its code is distributed under "GNU General Public License, Version 3" (see license.txt).


Why there is an open source plugin pack project for KLets?
--------
Commands directly integrated inside KLets are bounded to its release lifecycle, that usually is 2 or 3 month long due to heavy new features addition to its core.

Having a separate project where to add and maintain new and experimental commands allows me to provide faster release dates and bug fixing, regardless KLets development status.

In addition, this open source project provides a live example on how to write KLets plugins.

Also, it allows other developers to contribute for little bugs, new commands and new features addition to existing commands.


Why commands are available only in Italian and English?
--------
In order to provide acceptable delivery times and to remove delays, commands inside this plugin pack will be translated only in languages that I know: Italian (my native language) and English.

However, everyone can easily fork this repository in order to maintain and release it in other languages.

How to insall
--------
The Plugin Pack can be installed from the Play Store here: https://play.google.com/store/apps/details?id=com.voicecontrolapp.pluginpack


How to build the project
--------
- Open the pom.xml file and configure required properties, following their comments.
- Manually install the Foursquare client api library in your local Maven repository (the client artifact released by Foursquare on global maven repositories refers a parent pom which has never been released and Maven fails building projects depending on it). To do so:
  - Clone the github project from https://github.com/foursquare/foursquare-android-oauth
  - Checkout the release tag matching the version used by this plugin pack (check the pom)
  - Perform a local installation of the Foursquare client api project issuing a `mvn clean install` from the project's root directory
- Build the project APK issuing a `mvn clean install` from this project's root folder
