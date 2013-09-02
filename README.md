BukkitPluginSorter
==================

Sorts Bukkit plugins by number of downloads.

Outputs a basic html table in a file named after the plugin stage it scans.
eg for listing mature releases, it produces Mature.html

This has been thrown together fairly quickly, so the source may not be all that readable, but it works.
Uses jsoup to get and manipulate the html, uses threads to parallellise (and therefore speed up from a crawl) the retireval of pages.

Requires JSoup 1.7.2, java JDK 1.6 (uses 1.6's File class for hard disk io)

Compiling
=======
javac -cp jsoup-1.7.2.jar:. BukkitPluginSorter.java

Running
=======
to get a list of mature-stage projects sorted by downloads:

java -cp jsoup-1.7.2.jar:. BukkitPluginSorter m

to get a list of release-stage projects sorted by downloads (warning: very slow, takes about 30 minutes to get ~6800 plugins due to bukkit's site)

java -cp jsoup-1.7.2.jar:. BukkitPluginSorter m
