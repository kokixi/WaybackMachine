import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.security.MessageDigest;


public class WaybackMachine {

    private static WayBackMachineStore websites;

    public static void main(String[] args) throws Exception {

        websites = new WayBackMachineStore();

        //Populate websites information
        populateData();

        //Create server
        int port = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new WayBackMachineHandler());
        server.createContext("/content", new PageHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("server listening in port " + port + "...");

    }

    /**
     * Populate websites information into the WayBackMachineStore object
     */


    public static void populateData() {

        websites.insert("www.google.com", "2009-02-04", "f2d787451893fcf10385142f8d44fc7b");
        websites.insert("www.tripadvisor.com", "2006-01-06", "c9da795bf9d8c797dccec7ef41f3fa13");
        websites.insert("www.tripadvisor.com", "2008-08-31", "43f6599cbbead43b09f5da5991c235b2");
        websites.insert("www.tripadvisor.com", "2009-03-08", "a16f048246657c5fdedb736a409f6ed7");
        websites.insert("www.tripadvisor.com", "2012-02-02", "c5ceea38f026f746ee2c214da681170c");
    }

    /**
     * Class to handle petitions from the client
     */

    static class WayBackMachineHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange t) throws IOException {
            // UI init
            UI ui = new UI();
            String response;

            if("GET".equals(t.getRequestMethod())) {
                // params handling

                String query = t.getRequestURI().getQuery();
                String url = null;
                String date = null;
                String webURL = null;
                String errorMsg = null;

                if(query != null) {
                    Map<String, String> queryParams = queryToMap(query);

                    url = queryParams.get("url");
                    date = queryParams.get("date");
                    try {

                        String nearestDate = websites.getNearestDate(url, date);
                        webURL = "/content?url=" + url + "&date=" + nearestDate;

                    } catch(Exception e){
                        errorMsg = e.getMessage();
                    }
                }

                // UI set up
                response = ui.print(url, date, webURL, errorMsg);
                reply(t, 200, response);

            } else {
                response = "This request method is not accepted.";
                reply(t, 301, response);
            }
        }

        /**
         * Get the params from the query
         * @param  query
         * @return hash with all the key-value pairs.
         */
        public Map<String, String> queryToMap(String query) {
            Map<String, String> result = new HashMap<String, String>();

            if(query != null) {
                for (String param : query.split("&")) {
                    String pair[] = param.split("=");
                    if (pair.length>1) {
                        result.put(pair[0], pair[1]);
                    }else{
                        result.put(pair[0], "");
                    }
                }
            }

            return result;
        }

        /**
         * Create and send the response to the server
         * @param  t - HttpExchange object
         * @param  code is the response code to send to the server
         * @param  payload is the response
         * @throws IOException
         */
        public void reply(HttpExchange t, int code, String payload) throws IOException {

            byte[] responseBytes = payload.getBytes();
            OutputStream os = t.getResponseBody();

            t.sendResponseHeaders(code, responseBytes.length);

            os.write(responseBytes);
            os.close();
        }

    }

    /**
     * Class to create the website required by the user
     */

    static class PageHandler extends WayBackMachineHandler {

        Cache cache = new Cache();

        @Override
        public void handle(HttpExchange t) throws IOException {

            String query = t.getRequestURI().getQuery();
            String url = null;
            String date = null;
            String file = "";
            String response = "";

            if(query != null) {
                Map<String, String> queryParams = queryToMap(query);

                url = queryParams.get("url");
                date = queryParams.get("date");
                try {
                    file = websites.get(url, date);
                } catch(Exception e){
                    reply(t, 500, e.getMessage());
                    return;
                }
            }

            try {

                String siteID = url+date;

                if(cache.isRecentSite(siteID)){
                    response = cache.getPage(siteID);
                }
                else {
                    response = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8);
                    cache.insertSite(siteID, response);
                }

                reply(t, 200, response);

            }catch(IOException e){
                reply(t, 500, e.getMessage());
            }
        }
    }

    /**
     * Class to create the html to be sent
     */

    static class UI {

        public String print(String url, String date, String requestedPage, String errorMsg) {
            String urlData = url == null ? "" : url;
            String dateData = date == null ? "" : date;
            String page = requestedPage == null ? "" : requestedPage;

            if(dateData == ""){
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                Date d = new Date();
                dateData = dateFormat.format(d);
            }

            String result = "<html>" +
                                "<body>" +
                                    "<form name=\"form1\" method=\"get\" action=\".\">" +
                                        "<input type=\"text\" name=\"url\" size=\"100\" autofocus placeholder=\"www.google.com\" style=\"font-size: 20px; max-width: 600px; vertical-align: middle \" value=\"" + urlData + "\">" +
                                        "<input type=\"date\" name=\"date\" style=\"margin-left: 30px; vertical-align: middle \" value=\"" + dateData + "\">" +
                                        "<input type=\"submit\" value=\"submit\" style=\"visibility: hidden;\">" +
                                    "</form>" +
                                    (page != "" && errorMsg == null ? ("<iframe src=\"" + page + "\" width=\"100%\" height=\"100%\"></iframe>") : "") +
                                    (errorMsg != null ? "<p>" + errorMsg + "</p>" : "") +
                                "</body>" +
                            "</html>";

            return result;
        }

    }

    /**
     * Class to store the websites information
     */
    static class WayBackMachineStore {

        private HashMap<String, ArrayList<VersionDomain> > domains = new HashMap<>();

        /**
         * Insert a new website
         * @param url of the website
         * @param date
         * @param checksum
         */
        public void insert(String url, String date, String checksum) {
            if(domains.containsKey(url)) {
                ArrayList<VersionDomain> urlVersions = domains.get(url);
                if(!urlVersions.get(urlVersions.size()-1).checksum.equals(checksum)) {
                    VersionDomain vd = new VersionDomain(date, checksum);
                    urlVersions.add(vd);
                }
            } else {
                ArrayList<VersionDomain> urlVersions = new ArrayList<>();
                VersionDomain vd = new VersionDomain(date, checksum);
                urlVersions.add(vd);
                domains.put(url, urlVersions);
            }
        }

        /**
         * Get the path to the website
         * @param  url
         * @param  date
         * @return path to the information
         * @throws Exception
         */

        public String get(String url, String date) throws Exception {
            String file = "";

            if(url != null && date != null) {
                String nearestDate = getNearestDate(url, date);
                file = "./data/" + toMD5(url) + "/" + getTimestamp(nearestDate) + ".html";
            }

            return file;
        }

        /**
         * Get the nearest date to the given date
         * @param  url
         * @param  date
         * @return nearest date
         * @throws Exception throw exception if the url is not found or there is no version available for the given date
         */
        public String getNearestDate(String url, String date) throws Exception{

            if(domains.containsKey(url)) {

                ArrayList<VersionDomain> versions = domains.get(url);
                if(versions.get(0).date.compareTo(date) <= 0) {
                    int min = 0;
                    int max = versions.size();
                    int mid = (min + max)/2;

                    if(versions.get(versions.size() - 1).date.compareTo(date) < 0)
                        return versions.get(versions.size() - 1).date;
                    else {
                        while(min <= max) {

                            mid = (min + max)/2;

                            String potentialDate = versions.get(mid).date;
                            if(potentialDate.equals(date))
                                return potentialDate;
                            else
                                if(potentialDate.compareTo(date) < 0)
                                    min = mid + 1;
                                else
                                    max = mid - 1;

                        }

                    }
                    return versions.get(max).date;

                } else {
                    throw new Exception("Version not found for the given date");
                }

            } else {
                throw new Exception("Domain not found");
            }

        }

        /**
         * Convert a date to timestamp
         * @param  date
         * @return timestamp for the given date
         * @throws Exception
         */
        public static String getTimestamp(String date) throws Exception{

            String time = "";

            try{
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
                Date d = formatter.parse(date);
                Timestamp tm = new Timestamp(d.getTime());

                time = String.valueOf(tm.getTime());

            }catch(Exception e){
                throw e;
            }

            return time;
        }

        /**
         * Convert a domain to MD5
         * @param  domain
         * @return MD5 value for the given domain
         * @throws Exception
         */

        public static String toMD5(String domain) throws Exception {

            StringBuffer domainBuffer = new StringBuffer();

            try{

                byte[] domainBytes = domain.getBytes();

                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] domainMD5 = md.digest(domainBytes);

                for (byte b : domainMD5) {
                    domainBuffer.append(String.format("%02x", b & 0xff));
                }

            }catch(Exception e){
                throw e;
            }

            return domainBuffer.toString();
        }

    }

    /**
     * Class to store date and checksum of every domain
     */

    static class VersionDomain {
        public String date;
        public String checksum;

        public VersionDomain(String d, String ck) {
            date = d;
            checksum = ck;
        }
    }

    /**
     * Class to keep track of the last five websites requested
     */

    static class Cache {
        ArrayList<SiteCache> recentSites;

        Cache() {
            recentSites = new ArrayList<>();
        }

        /**
         * Insert site to the cache
         * @param siteID
         * @param page
         */
        public void insertSite(String siteID, String page) {

            SiteCache site = new SiteCache(siteID, page);
            if(recentSites.size() < 5)
                recentSites.add(site);
            else {
                recentSites.add(4, site);
            }
        }

        /**
         * Check if the site is in the cache
         * @param  siteID
         * @return true if the site is in the cache
         */
        public boolean isRecentSite(String siteID) {

            for(int i = 0; i < recentSites.size(); i++) {
                if(recentSites.get(i).siteID.equals(siteID))
                    return true;
            }

            return false;
        }

        /**
         * Get the site information
         * @param  siteID
         * @return the website to be shown
         */
        public String getPage(String siteID) {

            boolean found = false;

            for(int i = 0; i < recentSites.size() && !found; i++) {
                if(recentSites.get(i).siteID.equals(siteID)) {
                    sortRecent(i);
                    found = true;
                }
            }

            return recentSites.get(0).page;

        }

        /**
         * Sort the cache
         * @param max sort the array from 0 to max
         */
        private void sortRecent(int max){

            SiteCache site = recentSites.get(max);

            for(int i = 1; i < max; i++) {
                recentSites.add(i + 1, recentSites.get(i));
            }

            recentSites.add(0, site);
        }
    }

    /**
     * Class to store the information of the last webs requested
     */

    static class SiteCache {
        public String siteID;
        public String page;

        SiteCache(String id, String page) {
            siteID = id;
            this.page = page;
        }
    }
}

