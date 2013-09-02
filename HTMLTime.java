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

/**Script to sort a list of plugins from dev.bukkit.org by number of downloads.
 * Prints the output in an HTML table.
 * @author Adam Howard (http://medavox.com)
 * @version 3 - now with threaded httpRequests, to parallelise blocking methods*/
public class HTMLTime implements Runnable
{
    private PrintStream o = System.out;//makes printing shorter to type
    private static final String BASEDIR = "http://dev.bukkit.org";
    private static final String MATURE  = BASEDIR+"/bukkit-plugins/?stage=m";
    private static final String RELEASE = BASEDIR+"/bukkit-plugins/?stage=r";
    private static final String BETA = BASEDIR+"/bukkit-plugins/?stage=r";
    private static final String USER_AGENT = "HTMLTime - a workaround script to sort bukkit plugins by downloads";//let them know my name! hopefully they will take this hint to redesign the website to avoid my unnecessary bandwidth usage
    private static final int TIMEOUT = 10000;
    private final int PROJECTS_PER_PAGE = 20;//number of project results per page
    private final int NUM_THREADS = 12;
    
    //'arguments' for threads
    private int numEntries;//actual number of projects
    private int lastThread = 0;//allows threads to work out their number
    private Project[] projects;//the actual, main, array of Projects
    private boolean doingDownloads = true;//false:populate projects array; true:get their download numbers
    private String url;//the url 'argument' passed to the getProjectEntries threads
    
    public HTMLTime(String releaseURL, String releasePrettyName)
    {
        numEntries = getNumEntries(releaseURL);
        url = releaseURL;
        //string must be a bukkit plugin list url
        o.println("execute getProjectEntries...");
        getProjectEntries(releaseURL);//get names, links and descriptions THREADED
        
        o.println("execute getNumDownloads...");
        getNumDownloads();//get num downloads -- NOW THREADED
        o.println("sorting...");
        quickSort(projects, 0, projects.length-1);//sort results
        String output = generateHTML(projects, releasePrettyName);
        
        try
        {
            File file = new File(releasePrettyName+".html");
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
    public void run()
    {
        lastThread++;
        int threadNum = lastThread;
        if(threadNum == NUM_THREADS)
        {
            lastThread = 0;
        }
        if(doingDownloads)
        {
            o.println("Download Number Thread "+threadNum+" started");
            int pagesDone = 0;
            for(int i = threadNum-1; i < projects.length; i+=NUM_THREADS)
            {
                Element bawdy;
                while(true)
                {
                    try
                    {
                        bawdy = Jsoup.connect(BASEDIR + projects[i].link)
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
                    Elements donluds = bawdy.getElementsByAttribute("data-value");
                    projects[i].downloads = Integer.parseInt(donluds.get(0).attr("data-value"));
                }
                catch(Exception e)
                {
                    System.err.println(e.getClass()+" in thread "+threadNum+":");
                    lazyExceptionHandler(e);
                }
                //receive the majesty of my fixed-width column printouts
                o.println(i+"/"+(projects.length-1)+":"+
                getSpacing(i+"/"+(projects.length-1), 10)+
                projects[i].name+
                getSpacing(projects[i].name, 32)+
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
                Elements descs;
                int pagesDone = 0;
                for(int h = threadNum; h <= numPages; h+=NUM_THREADS)
                {
                    while(true)
                    {
                        try
                        {
                            boday = Jsoup.connect(url + "&page="+h)
                            .timeout(TIMEOUT).userAgent(USER_AGENT).get().body();
                            break;
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
                    o.println("page "+h+"/"+numPages);
                    projs = boday.getElementsByClass("col-project");
                    descs = boday.getElementsByClass("summary");
                    projs.remove(0);//remove the table header
                    projs.remove(0);//remove the table header
                    int n = (h * PROJECTS_PER_PAGE) - PROJECTS_PER_PAGE;//iterator over all projects on all pages
                    o.println("Thread "+threadNum+" starting page "+h+" at "+n+
                        "-"+(n-PROJECTS_PER_PAGE));
                    for(int i = 0; i < projs.size(); i++)
                    {
                        projects[n] = new Project();
                        projects[n].name = projs.get(i).text();
                        projects[n].link = projs.get(i).getElementsByTag("a").attr("href");
                        projects[n].description = descs.get(i).ownText();
                        
                        o.println(n+":\t"+projects[n].name);
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
        o.println("about to make "+NUM_THREADS+" threads...");
        doingDownloads = false;
        for(int i = 0; i < NUM_THREADS; i++)
        {
            threads[i] = new Thread(this);
            threads[i].start();
        }
        for(int i = 0; i < NUM_THREADS; i++)
        {
            try
            {
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
            try
            {
                threads[i].join();
            }
            catch(InterruptedException e)
            {
                lazyExceptionHandler(e);
            }
        }
    }
    
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
    
    private String escapeChars(String in)
    {
        String out = in.replace("&", "&amp;");
        out = out.replace("<","&lt;");
        out = out.replace(">", "&gt;");
        out = out.replace("\"", "&quot;");
        out = out.replace("'", "&#x27;");
        return out.replace("/", "&#x2F;");
    }
    
    private String now()
    {
        String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        String date = DateFormat.getDateInstance(DateFormat.FULL).format(new Date());
        return time+" on "+date;
    }
    
    private String generateHTML(Project[] projs, String projStatusList)
    {
        String html = "<!DOCTYPE html>\n<html><head><title>"+projStatusList+
            "-Status Bukkit Plugins Sorted By Downloads</title></head>"+
            "\n<body><h1>Bukkit Plugins sorted by Downloads</h1><h2>Showing "
            +projStatusList+" Projects</h2><p>Generated at "+now()+"</p><table border=\"1\">"+
            "\n<tr><th>Name</th><th>Downloads</th><th>Summary</th></tr>";
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
                "</td><td>"+escapeChars(projs[i].description)+"</td></tr>";
            }
            html += "</table></body></html>";
        }
        return html;
    }
    
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
    
    private static void lazyExceptionHandler(Exception e)
    {
        System.err.println("Oh no! Some sort of error! fuck it, just dump it and exit.");
        System.err.println(e.getClass());
        System.err.println(e);
        System.err.println(e.getMessage());
        e.printStackTrace();
        System.exit(1);
    }

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
            return -1;
        }
    }
    private class Project
    {
        public String name;
        public String description;
        public String link;
        public int downloads;
    }
    
    public static void main(String[] args)
    {
        for (int i = 0; i < args.length; i++)
        {
            System.out.println("arg "+i+": "+args[i]);
        }
        HTMLTime bukkitParser;
        if(args.length >0)
        {
            if(args[0].equals("m"))
            {
                System.out.println("Getting MATURE...");
                bukkitParser = new HTMLTime(MATURE, "Mature");
            }
            else if (args[0].equals("r"))
            {
                System.out.println("Getting RELEASE...");
                bukkitParser = new HTMLTime(RELEASE, "Release");
            }
            else if (args[0].equals("b"))
            {
                System.out.println("Getting BETA...");
                bukkitParser = new HTMLTime(BETA, "Beta");
            }
            else
            {
                System.out.println("No valid argument given, assuming MATURE!\nGetting MATURE...");
                bukkitParser = new HTMLTime(MATURE, "Mature");
            }
        }
        else
        {
            System.out.println("No argument given, assuming MATURE!\nGetting MATURE...");
            bukkitParser = new HTMLTime(MATURE, "Mature");
        }
    }
}
