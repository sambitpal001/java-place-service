package com.wawa.webhook.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.slf4j.LoggerFactory.getLogger;

@Service
public class PactCreateWebhookService {
    private static final Logger LOG = getLogger(PactCreateWebhookService.class);

    LocalDateTime localTime = LocalDateTime.now();
    Date dt =Date.from(localTime.atZone(ZoneId.systemDefault()).toInstant());
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    String strCurrentDate = sdf.format(dt);
    /*
    Create the webhook based on the mentioned consumer
    */
    @EventListener(ApplicationReadyEvent.class)
    public void createPactWebhook() throws Exception {
        LOG.info("Get the webhooks! ");
        File directory = getFileFromURL();
        File[] listOfFiles = directory.listFiles();
        for(File listFile : listOfFiles) {
            String strWebhookModifed = sdf.format(listFile.lastModified());
            if(strWebhookModifed.equalsIgnoreCase(strCurrentDate)){
                LOG.info("Modifed file: " + listFile.getName());
                //JSON parser object to parse read file
                JSONParser jsonParser = new JSONParser();
                JSONObject data = (JSONObject) jsonParser.parse(new FileReader(listFile));
                String strConsumerName = (String)data.get("consumerName");
                String strProviderName = (String)data.get("providerName");
                String strProjectName = (String)data.get("projectName");
                String strPipelineName = (String)data.get("pipelineName");
                LOG.info("strConsumerName=" + strConsumerName +",,strProjectName="+strProjectName +",,strPipelineName="+strPipelineName);
                //GET for http://localhost:8500/webhooks/provider/inventory_provider/consumer/inventory_consumer
                Boolean blWebhookPresent = isGivenConsumerWehookExists(strConsumerName, strProviderName);
                //If Webhook not exists create a new webhook
                LOG.info("Webhook exists="+blWebhookPresent);
                if(!blWebhookPresent){
                    LOG.info("Creating a webhook");
                    //createWebhookForConsumer(strConsumerName,strProviderName,strProjectName,strPipelineName);
                   createOneWebhook(strConsumerName,strProviderName,strProjectName,strPipelineName);
                }
            }
        }
    }

    /*
    Create webhook in pactbroker
     */
    private void createWebhookForConsumer(String sConsumer, String sProvider, String sProjectName, String sPipelinename) throws Exception{
        String codeFreshTriggerURL="https://g.codefresh.io/api/pipelines/run/"+sProjectName+"%2F"+sPipelinename+"/";
        String localtriggerURL = "http:\\host.docker.internal:8040\builds\new";
        JSONObject consumerDetails = new JSONObject();
        consumerDetails.put("name",sConsumer);
        JSONObject consumerObject = new JSONObject();
        consumerObject.put("consumer", consumerDetails);

        JSONObject requestDetails = new JSONObject();
        requestDetails.put("method","POST");
        requestDetails.put("url",localtriggerURL);
        consumerObject.put("request",requestDetails);

        JSONObject headerDetails = new JSONObject();
        headerDetails.put("Accept", MediaType.APPLICATION_JSON);
        //headerDetails.put("Authorization",localtriggerURL);
        headerDetails.replace("},","}");
        requestDetails.put("headers",headerDetails);

        //events as an json array
        JSONObject eventDetails = new JSONObject();
        eventDetails.put("name","contract_content_changed");
        JSONArray eventListArry = new JSONArray();
        eventListArry.add(eventDetails);
        JSONObject eventObject = new JSONObject();
        consumerObject.put("events",eventListArry);


        StringWriter out = new StringWriter();
        consumerObject.writeJSONString(out);

        String consumerJsonWebhoook = out.toString();
        System.out.print("PPPPP=="+consumerJsonWebhoook);

        HttpClient client = HttpClient.newHttpClient();
        String strGetWebhookUrl = "http://localhost:8500/webhooks/provider/" + sProvider +"/consumer/" +sConsumer;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(strGetWebhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(consumerJsonWebhoook))
                .build();

        HttpResponse<String> createResponse = client.send(request,HttpResponse.BodyHandlers.ofString());
        String strResponseCreateWebhook = createResponse.body();
        LOG.info("Status Code="+createResponse.statusCode());
        LOG.info("Response for Get Webhook is : "+strResponseCreateWebhook);
    }
    /*
    Create webhook in pactbroker
     */
    private void createOneWebhook(String sConsumer, String sProvider, String sProjectName, String sPipelinename) throws Exception{
        LOG.info("Inside createOneWebhook.....");
        //String codeFreshTriggerURL="https://g.codefresh.io/api/pipelines/run/"+sProjectName+"%2F"+sPipelinename+"/";
        String localtriggerURL = "http://host.docker.internal:8040/builds/new";
        String jsonString = "{'consumer':{'name':'"+sConsumer+"'},"+
                             "'request':{'method':'POST',"+
                             "'url':'"+localtriggerURL+"',"+
                             "'headers':{'Accept':'application/json'}},"+
                             "'events':[{'name':'contract_content_changed'}]"+
                "}";
        JsonParser jsonParser = new JsonParser();
        JsonObject jsonObject = (JsonObject) jsonParser.parse(jsonString);

        System.out.print("OOOOO=="+jsonObject.toString());

        HttpClient client = HttpClient.newHttpClient();
        String strGetWebhookUrl = "http://localhost:8500/webhooks/provider/" + sProvider +"/consumer/" +sConsumer;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(strGetWebhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
                .build();

        HttpResponse<String> createResponse = client.send(request,HttpResponse.BodyHandlers.ofString());
        String strResponseCreateWebhook = createResponse.body();
        LOG.info("Status Code (gson.JsonObject)="+createResponse.statusCode());
        LOG.info("Response for create Webhook (gson.JsonObject) is : "+strResponseCreateWebhook);
    }

    /*
    Get the webhook present in pact broker with the given consumer and provider
     */
    private boolean isGivenConsumerWehookExists(String consumer, String provider) throws IOException, InterruptedException{
        Boolean blresponseWebhook = false;
        HttpClient client = HttpClient.newHttpClient();
        String strGetWebhookUrl = "http://localhost:8500/webhooks/provider/" + provider +"/consumer/" +consumer;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(strGetWebhookUrl))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,HttpResponse.BodyHandlers.ofString());
        String strResponseWebhook = response.body();
        LOG.info("Response for Get Webhook is : "+response.body());
        if(strResponseWebhook.contains("A webhook for the pact between "+consumer+" and "+provider)){
            LOG.info("Webhook present for consumer"+consumer +"and provider "+provider);
            blresponseWebhook = true;
        }else if(strResponseWebhook.contains("No consumer with name '"+consumer+"' found")){
            LOG.info("Error: No consumer entry with name "+consumer+" found in pact broker");
            blresponseWebhook = true;
        }
    return blresponseWebhook;
    }

    /*
        Gets a list of files in WebhookConfigs folder
     */
    private File getFileFromURL() {
        URL url = this.getClass().getClassLoader().getResource("WebhookConfigs");
        File file = null;
        try {
            file = new File(url.toURI());
        } catch (URISyntaxException e) {
            file = new File(url.getPath());
        } finally {
            return file;
        }
    }

}