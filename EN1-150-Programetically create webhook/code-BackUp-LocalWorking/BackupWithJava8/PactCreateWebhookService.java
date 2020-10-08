package com.wawa.webhook.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URI;
/*import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;*/
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.stream.Stream;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class PactCreateWebhookService {
    private static final Logger LOG = getLogger(PactCreateWebhookService.class);

    LocalDateTime localTime = LocalDateTime.now();
    Date dt =Date.from(localTime.atZone(ZoneId.systemDefault()).toInstant());
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String strCurrentDate = sdf.format(dt);
    @Value( "${event.name}" )
    private String strEventName;

    @Value( "${api.key}" )
    private String strApiKey;

    @Value( "${pact.url}" )
    private String strPactLocalBaseUrl;

    @Value( "${pact.codefresh.url}" )
    private String strPactCodeFreshBaseUrl;

    private PathMatchingResourcePatternResolver resourcePatternResolver;

    public PactCreateWebhookService() {
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
    }

    public Resource getResource(String path) {
        path = path.replace("\\", "/");
        return resourcePatternResolver.getResource(path);
    }

    public Resource[] getResources(String path) throws IOException {
        path = path.replace("\\", "/");
        return resourcePatternResolver.getResources(path);
    }
    public Resource[] getResourcesIn(String path) throws IOException {
        // Get root dir URI
        Resource root = getResource(path);
        String rootUri =  root.getURI().toString();

        // Search all resources under the root dir
        path = (path.endsWith("/")) ? path + "**" : path + "/**";

        // Filter only direct children
        return Arrays.stream(getResources(path)).filter(resource -> {
            try {
                String uri = resource.getURI().toString();

                boolean isChild = uri.length() > rootUri.length() && !uri.equals(rootUri + "/");
                if (isChild) {
                    boolean isDirInside = uri.indexOf("/", rootUri.length() + 1) == uri.length() - 1;
                    boolean isFileInside = uri.indexOf("/", rootUri.length() + 1) == -1;
                    return isDirInside || isFileInside;
                }

                return false;
            } catch (IOException e) {
                return false;
            }
        }).toArray(Resource[]::new);
    }
    public String[] getResourcesNamesIn(String path) throws IOException {
        // Get root dir URI
        Resource root = getResource(path);
        System.out.println("BBBBBBBB= "+root.getFilename());
        String rootUri = URLDecoder.decode(root.getURI().toString().endsWith("/") ? root.getURI().toString() : root.getURI().toString() + "/", "UTF-8");

        // Get direct children names
        return Arrays.stream(getResourcesIn(path)).map(resource -> {
            try {
                String uri = URLDecoder.decode(resource.getURI().toString(), "UTF-8");

                boolean isFile = uri.indexOf("/", rootUri.length()) == -1;
                if (isFile) {
                    return uri.substring(rootUri.length());
                } else {
                    return uri.substring(rootUri.length(), uri.indexOf("/", rootUri.length() + 1));
                }
            } catch (IOException e) {
                return null;
            }
        }).toArray(String[]::new);
    }

    /*
    Create the webhook based on the given consumer
    */
    @EventListener(ApplicationReadyEvent.class)
    public void createPactWebhook() throws IOException, InterruptedException, ParseException, URISyntaxException {
        LOG.info("Get the webhook details from WebhookConfigs folder inside /resource");
        ClassLoader cl = PactCreateWebhookService.class.getClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
        Resource resources[] = resolver.getResources("classpath:WebhookConfigs/*");
        for (Resource listFile : resources) {
            System.out.println("SSSSSSS=" + listFile.getFilename());
        }

        /*Resource[] folder = resourcePatternResolver.getResources("/WebhookConfigs");
        LOG.info("data LOCCC= {}",folder.length);
        for (Resource listFile : folder) {

            File x = listFile.getFile();
            String strFileName = listFile.getFilename();
            LOG.info("Modifed file Name : {} and File is {}", strFileName, x);
        }*/


       // URL url = this.getClass().getClassLoader().getResource("WebhookConfigs");

       // Path path = Paths.get("/WebhookConfigs/**");
        /*try (Stream<Path> paths = Files.walk(Paths.get("/WebhookConfigs"))) {
            paths.filter(Files::isRegularFile).forEach(System.out::println);
        }*/
       String strPath = this.getClass().getClassLoader().getResource("WebhookConfigs/").toExternalForm();
        String ssPath = null;
        /*if(strPath.contains("file:")){
            ssPath = strPath.replace("file:/", "");
        }else*/ if(strPath.contains("jar:")){
            ssPath = strPath.replace("jar:file:", "");
        }
       /* String [] strResourcenames = getResourcesNamesIn(ssPath);
        for (String listFile : strResourcenames) {
            LOG.info("Modifed file Name : {} ",listFile);
        }*/
        LOG.info("PATHH= {}",ssPath.replace("/","//"));
        //File directory = new File(getClass().getResource("/WebhookConfigs").toExternalForm());
        File directory = new File(ssPath);

        //URI uri = PactCreateWebhookService.class.getResource("/WebhookConfigs").toURI();
        //InputStream in = Model.class.getClassLoader().getResourceAsStream("/WebhookConfigs");
        //BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        LOG.info("directory= {}",directory.getPath());
        File[] listOfFiles = directory.listFiles();
        LOG.info("listOfFiles.length {}",listOfFiles.length);
        if(directory!=null){
        //File directory = new File(uri);
            //LOG.info("directory= {}",directory.getPath());
        //listOfFiles = directory.listFiles();
        LOG.info("listOfFiles.length {}",listOfFiles.length);
        for (File listFile : listOfFiles) {
            String strWebhookModifed = sdf.format(listFile.lastModified());
            LOG.info("Modifed time : {} and current time : {}", strWebhookModifed, strCurrentDate );
            if (strWebhookModifed.equalsIgnoreCase(strCurrentDate)) {
                String strFileName = listFile.getName();
                LOG.info("Modifed file Name : {}", strFileName );
                //JSON parser object to parse read file
                JSONParser jsonParser = new JSONParser();
                JSONObject data = (JSONObject) jsonParser.parse(new FileReader(listFile));
                String strConsumerName = (String) data.get("consumerName");
                String strProviderName = (String) data.get("providerName");
                String strProjectName = (String) data.get("projectName");
                String strPipelineName = (String) data.get("pipelineName");
                LOG.info("strConsumerName= {}, strProviderName= {}, strProjectName= {} ", strConsumerName, strProviderName, strProjectName);
                //GET for http://localhost:8500/webhooks/provider/inventory_provider/consumer/inventory_consumer
                Boolean blWebhookPresent = isGivenConsumerWehookExists(strConsumerName, strProviderName);
                //If Webhook not exists create a new webhook
                LOG.info("Webhook exists= {}",blWebhookPresent);
                if (!blWebhookPresent) {
                    LOG.info("Creating a webhook");
                    createOneWebhook(strConsumerName, strProviderName, strProjectName, strPipelineName);
                }
            }
        }
    }else{
            LOG.error("No Files at /WebhookConfigs folder");
        }
    }
    /*
    Create webhook in pactbroker
     */
    private void createOneWebhook(String sConsumer, String sProvider, String sProjectName, String sPipelinename) throws  IOException, InterruptedException{
        LOG.info("Inside createOneWebhook.....");
        String strTriggerURL=strPactCodeFreshBaseUrl+sProjectName+"%2F"+sPipelinename+"/";
        String jsonString = "{'consumer':{'name':'"+sConsumer+"'},"+
                             "'request':{'method':'POST',"+
                             "'url':'"+strTriggerURL+"',"+
                             "'headers':{'Accept':'application/json'," +
                             "'Authorization':'bearer "+strApiKey+"'}},"+
                             "'events':[{'name':'"+strEventName+"'}]"+
                "}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = (JsonObject) jsonParser.parse(jsonString);
        String strPreparedJsonString = jsonObject.toString();

        LOG.info("My JSON String is = {}",strPreparedJsonString);

       /* HttpClient client = HttpClient.newHttpClient();
        String strGetWebhookUrl = strPactLocalBaseUrl + sProvider +"/consumer/" +sConsumer;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(strGetWebhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                .build();

        HttpResponse<String> createResponse = client.send(request,HttpResponse.BodyHandlers.ofString());
        String strResponseCreateWebhook = createResponse.body();
        int intResponseCode = createResponse.statusCode();
        LOG.info("Status Code (gson.JsonObject)= {}", intResponseCode);
        LOG.info("Response for create Webhook (gson.JsonObject) is : {}", strResponseCreateWebhook);*/
    }

    /*
    Get the webhook present in pact broker with the given consumer and provider
     */
    private boolean isGivenConsumerWehookExists(String consumer, String provider) throws IOException, InterruptedException{
        Boolean blresponseWebhook = false;
       /* HttpClient client = HttpClient.newHttpClient();
        String strGetWebhookUrl = strPactLocalBaseUrl + provider +"/consumer/" +consumer;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(strGetWebhookUrl))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,HttpResponse.BodyHandlers.ofString());
        String strResponseWebhook = response.body();
        LOG.info("Response for Get Webhook is : {}", strResponseWebhook);
        if(strResponseWebhook.contains("A webhook for the pact between "+consumer+" and "+provider)){
            LOG.info("Webhook present for consumer {} and provider {} ",consumer, provider);
            blresponseWebhook = true;
        }else if(strResponseWebhook.contains("No consumer with name '"+consumer+"' found")){
            LOG.info("Error: No consumer entry with name {} found in pact broker", consumer);
            blresponseWebhook = true;
        }*/
    return blresponseWebhook;
    }
}