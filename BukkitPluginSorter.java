import org.jsoup.*;
import java.io.PrintStream;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.text.NumberFormat;
import java.text.DateFormat;
import java.util.Date;


 //TODO:categories
 //documentation/comments
 //more commandline options
    //number of threads
    //timeout
    //whether to retrieve categories
    //whether to retrieve summaries
 //differential updating: saving results and only adding new stuff
    //(or at least skipping the initial step if there're still the same number of results, 
 /**Script to sort a list of plugins from dev.bukkit.org by number of downloads.
 * Prints the output in an HTML table.
 * PS for anyone else reading this, I tend to use poorly-spelled variable names to indicate temporary status
 * @author Adam Howard (http://medavox.com)
 * @version 4 - added categories*/
public class BukkitPluginSorter implements Runnable
{
    private PrintStream o = System.out;//makes printing shorter to type
    private static final String BASEDIR = "http://dev.bukkit.org";
    private static final String MATURE  = BASEDIR+"/bukkit-plugins/?stage=m";
    private static final String RELEASE = BASEDIR+"/bukkit-plugins/?stage=r";
    private static final String BETA = BASEDIR+"/bukkit-plugins/?stage=r";
    
    /**let them know my name! hopefully they will take this hint to redesign the website to avoid my bandwidth bombing*/
    private static final String USER_AGENT = "BukkitPluginSorter - a workaround script to sort bukkit plugins by downloads";
    /**milliseconds before a page request times out. Not as important now I've implemented retries*/
    private static final int TIMEOUT = 10000;
    /**Number of project results per page. Just a magic number due to their site design.*/
    private final int PROJECTS_PER_PAGE = 20;
    /**Number of parallel page requests, for both processes*/
    private final int NUM_THREADS = 30;
    /**does run() get the project names and number, or the number of downloads?*/
    private boolean getSummaries = false;
    
    //'arguments' for threads
    private int numEntries;//actual number of projects
    private int lastThread = 0;//allows threads to work out their number
    private Project[] projects;//the actual, main, array of Projects
    private boolean doingDownloads = true;//false:populate projects array; true:get their download numbers
    private String url;//the url 'argument' passed to the getProjectEntries threads
    
    /**Calls each individual step's method. Writes the resultant HTML
     * document to a file, based on which stage we selected.
     * @param URL the project results page to scan
     * @param stagePrettyName The pretty-print name of the stage we're scanning, eg. "Mature" */
    public BukkitPluginSorter(String URL, String stagePrettyName)
    {
        numEntries = getNumEntries(URL);//total number of projects
        url = URL;//thread 'argument': passes the URL we parse later
        o.println("getting project entries...");
        getProjectEntries(URL);//get names, links and descriptions - THREADED
        
        o.println("getting Number of Downloads...");
        getNumDownloads();//get num downloads - THREADED!
        o.println("sorting...");
        quickSort(projects, 0, projects.length-1);//sort results
        String output = generateHTML(projects, stagePrettyName);
        
        try
        {
            File file = new File(stagePrettyName+".html");
            if(!file.exists())
            {
                file.createNewFile();
            }
            FileWriter fw = new FileWriter(file);
            o.println("writing sorted results to file...");
            fw.write(output, 0, output.length());
            fw.close();
        }
        catch(Exception e)
        {
            lazyExceptionHandler(e);
        }
    }
    
    /*threaded http requests are the new black.
     * also run() is the new getProjectEntries().*/
     /**The main worker thread method. Depending on the state of doingDownloads,
      * Either spawns NUM_THREADS number of threads to (false): parallely retrieve
      *  and parse each page of results from the URL in url,
      * or (true), access each project's page and retrieve its number of downloads.*/
    public void run()
    {
        lastThread++;//thread numbers start from 1
        int threadNum = lastThread;//thread number affects which chunk of data that thread processes
        if(threadNum == NUM_THREADS)
        {/*if there are no more threads to spawn and number, reset this counter
			for later, when we do run() again*/
            lastThread = 0;
        }
        if(doingDownloads)//getting number of downloads value,
        {//from each individual project page
            o.println("Download Number Thread "+threadNum+" started");
            int pagesDone = 0;//counts work done per thread
            for(int i = threadNum-1; i < projects.length; i+=NUM_THREADS)//splits work between threads
            {
                Element body;
                while(true)//keep trying to download the page until successful,
                {//retrying on SocketTimeoutException or IOException
                    try
                    {
                        body = Jsoup.connect(BASEDIR + projects[i].link)
                            .timeout(TIMEOUT)
                            .userAgent(USER_AGENT)
                            .get()
                            .body();
                        break;
                    }
                    catch(SocketTimeoutException stoe)
                    {
                        System.err.println("Thread "+threadNum+
                            ": TIMEOUT getting downloads of "+projects[i].name+
                            "; trying again...");
                    }
                    catch(IOException ioe)
                    {
                        System.err.println("Thread "+threadNum+
                            ": IOException getting downloads of "
                            +projects[i].name+"; trying again...");
                    }
                }
                try
                {
                    Elements donluds = body.getElementsByAttribute("data-value");
                    projects[i].downloads = Integer.parseInt(donluds.get(0).attr("data-value"));
                }
                catch(Exception e)
                {
                    System.err.println(e.getClass()+" in thread "+threadNum+":");
                    lazyExceptionHandler(e);
                }
                //behold the majesty of my fixed-width column printouts
                o.println(i+"/"+(projects.length-1)+":"+
                getSpacing(i+"/"+(projects.length-1), 10)+
                projects[i].name+
                getSpacing(projects[i].name, 30)+
                projects[i].downloads);
                pagesDone++;
            }
            o.println("Thread "+threadNum+" processed "+pagesDone+" pages.");
        }
        else//populating Project[] array with name, link and description
        {
            o.println("Array initialiser thread "+threadNum+" started");
            try
            {
                int numPages = numEntries / PROJECTS_PER_PAGE;
                numPages += numEntries % PROJECTS_PER_PAGE == 0 ? 0 : 1;//add 1 if there's a remainder
                projects = new Project[numEntries];
                
                Element boday;//body on each page of results
                Elements projs;//'element[]' containing project names
                Elements descs;//description
                int pagesDone = 0;
                for(int h = threadNum; h <= numPages; h+=NUM_THREADS)
                {
                    
                    while(true)
                    {
                        try
                        {
                            boday = Jsoup.connect(url + "&page="+h)
                            .timeout(TIMEOUT).userAgent(USER_AGENT).get().body();
                            break;//downloaded successfully; exit loop
                        }
                        catch(SocketTimeoutException stoe)
                        {
                            System.err.println("Thread "+threadNum+
                            ": TIMEOUT on page "+h+"; trying again...");
                        }
                        catch(IOException ioe)
                        {
                            System.err.println("Thread "+threadNum+
                                ": IOException downloading page "
                                +h+"; trying again...");
                        }
                    }
                    projs = boday.getElementsByClass("col-project");
                    descs = boday.getElementsByClass("summary");
                    projs.remove(0);//remove the table header
                    projs.remove(0);//remove the table header
                    int n = (h * PROJECTS_PER_PAGE) - PROJECTS_PER_PAGE;//iterator over all projects on all pages
                    o.println("Thread "+threadNum+" getting page "+h+"/"+numPages+" ("+n+
                        "-"+(n-PROJECTS_PER_PAGE)+")");
                    for(int i = 0; i < projs.size(); i++)
                    {
                        projects[n] = new Project();//initialise
                        projects[n].name = projs.get(i).text();//store name
                        projects[n].link = projs.get(i).getElementsByTag("a").attr("href");//store link
                        if(getSummaries)//optionally store summaries
						{projects[n].description = descs.get(i).ownText();}
						
                        //prints 20 lines per results page, a bit much
                        //o.println(n+":\t"+projects[n].name);
                        n++;
                    }
                    pagesDone++;
                }
                o.println("Thread "+threadNum+" processed "+pagesDone+" pages.");
            }
            catch(Exception e)
            {
                System.err.println(e.getClass()+" in thread "+threadNum+":");
                lazyExceptionHandler(e);
            }
        }
    }
    //begin getting projects: their names, number, descriptions and links    
    public void getProjectEntries(String url)
    {
        Thread[] threads = new Thread[NUM_THREADS];
        //o.println("about to make "+NUM_THREADS+" threads...");
        doingDownloads = false;//we're doing the other thing
        for(int i = 0; i < NUM_THREADS; i++)
        {
            threads[i] = new Thread(this);
            threads[i].start();
        }
        for(int i = 0; i < NUM_THREADS; i++)
        {
            try//wait until all threads have proceeded before continuing to the next step
            {//prevents the next method trying to process data that isn't there yet
                threads[i].join();
            }
            catch(InterruptedException e)
            {
                lazyExceptionHandler(e);
            }
        }
    }
    //begin investigating number of downloads for each project
    private void getNumDownloads()
    {
        doingDownloads = true;
        Thread[] threads = new Thread[NUM_THREADS];
        for(int i = 0; i < NUM_THREADS; i++)
        {
            threads[i] = new Thread(this);
            threads[i].start();
        }
        for(int i = 0; i < NUM_THREADS; i++)
        {
            try//wait until all threads have proceeded before continuing to the next step
            {//prevents the next method trying to process data that isn't there yet
                threads[i].join();
            }
            catch(InterruptedException e)
            {
                lazyExceptionHandler(e);
            }
        }
    }
    /**Provides the correct number of spaces to make equal-width columns
     * @param stringToSpace the column left of the spaces
     * @param charsFromStringStart the desired spacing
     * charsFromDesiredSpacing measures from the first character of the left 
     * column, to the first character of the right column, so its value should be
     * at least as large as the length of the largest left column entry.
     * @return the spaces to insert between the two columns*/
    private String getSpacing(String stringToSpace, int charsFromStringStart)
    {
        int numSpaces = charsFromStringStart - stringToSpace.length();
        if(numSpaces < 0)
        {return " ";}
        String spacing = "";
        for(int i = 0; i < numSpaces; i++)
        {spacing += " ";}
        return spacing;
    }
    /**Escapes non-HTML-safe characters from a string.
     * @param in the String to escape characters from
     see https://www.owasp.org/index.php/XSS_%28Cross_Site_Scripting%29_Prevention_Cheat_Sheet#RULE_.231_-_HTML_Escape_Before_Inserting_Untrusted_Data_into_HTML_Element_Content
     @return the String with all the offending characters replace with &these;.
     * */
    private String escapeChars(String in)
    {
        String out = in.replace("&", "&amp;");
        out = out.replace("<","&lt;");
        out = out.replace(">", "&gt;");
        out = out.replace("\"", "&quot;");
        out = out.replace("'", "&#39;");
        return out.replace("/", "&#47;");
    }
    /**Returns a string representing the moment (minute-precision) that the method was called.
     @return String of the form "<short time format> at <FULL date format>*/
    private String now()
    {
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        String date = DateFormat.getDateInstance(DateFormat.FULL).format(new Date());
        return time+" on "+date;
    }
    /**Generates the HTML output, creating a table with names(as links) and downloads.
     * Optionally also provides summaries, and categories(TODO).
     * @param projs The main list of projects used throughout this program
     * @param projStageList a pretty-print string naming the stage we're listing, eg. "Mature"
     * @return an HTML document as a String, for writing to a file*/
    private String generateHTML(Project[] projs, String projStageList)
    {
        String html = "<!DOCTYPE html>\n<html><head><title>"+projStageList+
            "-Stage Bukkit Plugins Sorted By Downloads</title></head>"+
            "\n<body><h1>Bukkit Plugins sorted by Downloads</h1><h2>Showing "
            +projStageList+"-Stage Projects</h2><p>Generated at "+now()+"</p><table border=\"1\">"+
            "\n<tr><th>Name</th><th>Downloads</th>"+(getSummaries ? "<th>Summary</th>" : "")+"</tr>";
        if(projs.length==1)
        {
            System.err.println("did not successfully get list of projects!");
            System.exit(1);
        }
        else
        {
            for(int i = 0; i < projs.length; i++)
            {
                html += "\n<tr><td><a href=\""+
                BASEDIR+projs[i].link+
                "\">"+projs[i].name+"</a></td><td>"+
                NumberFormat.getNumberInstance().format(projs[i].downloads)+
                "</td>"+
                (getSummaries ? "<td>"+escapeChars(projs[i].description)+"</td>":"")+"</tr>";
            }
            html += "</table></body></html>";
        }
        return html;
    }
    /**Descending-order quicksort implementation. Uses download value of each Project
     * to sort by.
     * @param projs The main list of projects used throughout this program
     * @param low The index at which to start sorting
     * @param high The index at which to end sorting  */
    public static void quickSort (Project[] projs, int low, int high)
	{
		int i=low;
		int j=high;
		Project temp;
		int middle = projs[(low+high)/2].downloads;

		while (i < j)
		{
			while (projs[i].downloads > middle)
			{
				i++;
			}
			while (projs[j].downloads < middle)
			{
				j--;
			}
			if (j >= i)
			{
				temp = projs[i];
				projs[i] = projs[j];
				projs[j] = temp;
				i++;
				j--;
			}
		}

		if (low < j)
		{
			quickSort(projs, low, j);
		}
		if (i < high)
		{
			quickSort(projs, i, high);
		}
	}
    /**Handles pretty much all exceptions thrown by this program.
     * Prints the error type, its message, and a stack trace to the standard error
     * stream, then exits the program with an error code of 1.
     * Some Exception catchers print some more specific information, then call this;
     * most just come straight here.
     * An exception (ha) to this generalisation is that SocketTimeoutExceptions 
     * and IOExceptions are consumed by the object attempting to get a webpage, 
     * and the method is retried until it succeeds (due to the unpredictable nature
     * of internet connections).
     * @param e The Exception we're lazily handling*/
    private static void lazyExceptionHandler(Exception e)
    {
        System.err.println("Oh no! Some sort of error! meh, just dump it and exit.");
        System.err.println(e.getClass());
        System.err.println(e);
        System.err.println(e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }
	/**Provides the total number of projects by the results page. This is the
	 * number we'll be accessing every individual page of, so the more there are,
	 * the slower we go. This number is obviously affected by the options we provide,
	 * such as category or project stage to scan.
	 * @param url the URL to scan
	 * @return an int of the total number of projects*/
    public static int getNumEntries(String url)
    {
        try
        {
            Element boday = Jsoup.connect(url).timeout(TIMEOUT).userAgent(USER_AGENT).get().body();
            Elements numps = boday.getElementsByClass("listing-pagination-pages-total");
            return Integer.parseInt(numps.get(0).ownText().split(" ")[0]);
        }
        catch(Exception e)
        {
            lazyExceptionHandler(e);
            return -1;//pointless, but prevents java complaining about a lack of return statement
        }
    }
    /**Stores data about each project in a convenient record.
     * Doesn't bother with java get/set convention, I wrote this in a hurry,
     * and all those methods slow development down without a big IDE*/
    private class Project
    {
        public String name;
        public String description;
        public String link;
        public int downloads;
        public String[] categories;//a project can be in >1 category
    }
    /**The main method. parses commandline options and passes on respective
     * arguments to the constructor*/
    public static void main(String[] args)
    {/*
        for (int i = 0; i < args.length; i++)
        {
            System.out.println("arg "+i+": "+args[i]);
        }*/
        BukkitPluginSorter bukkitParser;
        if(args.length >0)
        {
            if(args[0].equals("m"))
            {
                System.out.println("Getting MATURE...");
                bukkitParser = new BukkitPluginSorter(MATURE, "Mature");
            }
            else if (args[0].equals("r"))
            {
                System.out.println("Getting RELEASE...");
                bukkitParser = new BukkitPluginSorter(RELEASE, "Release");
            }
            else if (args[0].equals("b"))
            {
                System.out.println("Getting BETA...");
                bukkitParser = new BukkitPluginSorter(BETA, "Beta");
            }
            else
            {
                System.out.println("No valid argument given, assuming MATURE!\nGetting MATURE...");
                bukkitParser = new BukkitPluginSorter(MATURE, "Mature");
            }
        }
        else
        {
            System.out.println("No argument given, assuming MATURE!\nGetting MATURE...");
            bukkitParser = new BukkitPluginSorter(MATURE, "Mature");
        }
    }
}
