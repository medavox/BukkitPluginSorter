BukkitPluginSorter
==================

Sorts Bukkit plugins by number of downloads.

Outputs a basic html table in a file named after the plugin stage it scans.
eg for listing mature releases, it produces Mature.html

*Uses jsoup to get and manipulate the html

*uses threads to parallellise (and therefore speed up from a crawl) the retrieval of pages.

Requires JSoup 1.7.2 and JDK 1.6 (uses 1.6's File class for hard disk io, which is redesigned in 1.7)

Compiling
=======
javac -cp jsoup-1.7.2.jar:. BukkitPluginSorter.java

Running
=======
to get a list of mature-stage projects sorted by downloads:

java -cp jsoup-1.7.2.jar:. BukkitPluginSorter m

to get a list of release-stage projects sorted by downloads (warning: very slow, takes ~10 minutes to get ~6800 plugins due to bukkit's site)

java -cp jsoup-1.7.2.jar:. BukkitPluginSorter m
