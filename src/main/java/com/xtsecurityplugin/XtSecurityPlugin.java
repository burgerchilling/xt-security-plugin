package com.xtsecurityplugin;

import com.google.gson.Gson;
import okhttp3.*;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@PluginDescriptor(
        name = "XT Security Plugin",
        description = "This plugin talks to an https server to retrieve runescape login details and randomly takes screenshots every couple of minutes.",
        tags = {"xtsecurityplugin", "xt security plugin"}
)

public class XtSecurityPlugin extends Plugin {

    static final String CONFIG_GROUP_KEY = "xtsecurityplugin";

    private static final String SCREENSHOT_PATH = "screenshotupdate.jpg";

    private String BACKEND_URL = "";
    private String TokenSaved = "";

    @Inject
    private Client client;


    @Inject
    private ClientThread clientThread;

    @Inject
    private XtSecurityConfig config;

    @Inject
    private ClientUI clientUI;

    @Inject
    private DrawManager drawManager;
    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private Gson gson;  // Inject the Gson instance provided by the client

    @Inject
    private OkHttpClient okHttpClient;  // Inject the client's OkHttpClient instance

    private ScheduledExecutorService executorService;

    @Provides
    XtSecurityConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(XtSecurityConfig.class);
    }

    @Override
    public void startUp() {
//        System.out.println("started plugin!");

        executorService = Executors.newSingleThreadScheduledExecutor();

        // Schedule the task to run every 10 MINUTES
        executorService.scheduleAtFixedRate(this::performAction, 1, 10, TimeUnit.MINUTES);

        if (!BACKEND_URL.isEmpty() && !TokenSaved.isEmpty()) {

            // SEND MESSAGE PLUGIN STARTED
            performActionSendMessage(TokenSaved, "plugin started");
        }

    }


    @Override
    public void shutDown() {


        if (!BACKEND_URL.isEmpty() && !TokenSaved.isEmpty()) {

            // SEND MESSAGE PLUGIN STOPPED
            performActionSendMessage(TokenSaved, "plugin was stopped");
        }


    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) throws IOException {


        if (config.loginButton() && !config.accountid().isEmpty()) {


            if (client.getGameState() != GameState.LOGGED_IN) {

                // FETCH DATA
                performActionLogin(config.accountid());

            }


        }


    }

    private void performAction() {
        // This method will be executed every 10 minutes
//        System.out.println("Action performed at " + System.currentTimeMillis());


        if (!BACKEND_URL.isEmpty()) {
//            System.out.println("String is not empty");

            // Take the screenshot
            takeScreenshot();
        }


    }


    class Person {

        private String EMAIL;
        private String PASS;
        private String URLTarget;


        public String getEmail() {
            return EMAIL;
        }

        public String getPass() {
            return PASS;
        }

        public String getUrlTarget() {
            return URLTarget;
        }


        public void setEmail(String EMAIL) {
            this.EMAIL = EMAIL;
        }

        public void setPass(String PASS) {
            this.PASS = PASS;
        }

        public void setUrlTarget(String URLTarget) {
            this.URLTarget = URLTarget;
        }
    }


    private void performActionLogin(String accountid) {


        // Sample JSON data to send in the POST body
        String jsonData = "{ \"token\": \"" + accountid + "\"}";

        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        // Create the RequestBody
        RequestBody body = RequestBody.create(JSON, jsonData);

        // Build the POST request
        Request request = new Request.Builder()
                .url("https://www.xt.xtgroup.online/api/check")  // target URL
                .post(body)  // Set the POST body
                .addHeader("Authorization", "Bearer your-token")
                .addHeader("Content-Type", "application/json")
                .build();

        // Make the call
        Call call = okHttpClient.newCall(request);

        // Execute the request in a separate thread to avoid blocking the UI
        new Thread(() ->
        {
            try
            {
                // Execute the request and get the response
                Response response = call.execute();
                if (response.isSuccessful())
                {

//                    System.out.println("POST Successful ");
                    String responseBody = response.body().string();

                    // Set the response data into the class variables
                    Person person = gson.fromJson(responseBody, Person.class);

//                    System.out.println("URL: " + person.getUrlTarget());
//                    System.out.println("Pass: " + person.getPass());
//                    System.out.println("Email: " + person.getEmail());


                    client.setUsername(person.getEmail());
                    client.setPassword(person.getPass());

                    TokenSaved = config.accountid();
                    BACKEND_URL = person.getUrlTarget();


//                    System.out.println("URL Target Now: " + BACKEND_URL);
//                    System.out.println("TOKEN SAVED Now: " + TokenSaved);

                    person.setPass("");
                    person.setEmail("");

//                    System.out.println("Pass: " + person.getPass());
//                    System.out.println("Email: " + person.getEmail());
                }
                else
                {
//                    System.err.println("POST Failed: " + response.code());
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }).start();


    }


    private void takeScreenshot() {
        Consumer<Image> imageCallback = (img) ->
        {
            // This callback is on the game thread, move to executor thread
            executor.submit(() -> saveScreenshot(img));
        };

        drawManager.requestNextFrameListener(imageCallback);
    }


    private void saveScreenshot(Image image) {
        try {

            // Capture screenshot
            BufferedImage screenshot = ImageUtil.bufferedImageFromImage(image);

            // Save the screenshot to a file
            ImageIO.write(screenshot, "jpg", new File(SCREENSHOT_PATH));

//            System.out.println("Screenshot saved: " + SCREENSHOT_PATH);

            uploadImage(SCREENSHOT_PATH);


        } catch (IOException ex) {
            System.err.println("Error capturing screenshot: " + ex.getMessage());
        }
    }

    private void uploadImage(String filePath) {
        File imageFile = new File(filePath);

        // Define the MediaType for the file
        MediaType mediaType = MediaType.parse("image/jpg");

        // Create the RequestBody for the file
        RequestBody fileBody = RequestBody.create(mediaType, imageFile);

        // Build the multipart request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFile.getName(), fileBody)
                .build();


        // Build the POST request
        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .post(requestBody)  // Set the POST body
                .header("Authorization", "Client-ID " + TokenSaved)
                .build();

        // Make the call
        Call call = okHttpClient.newCall(request);

        // Execute the request in a separate thread to avoid blocking the UI
        new Thread(() ->
        {
            try
            {
                // Execute the request and get the response
                Response response = call.execute();
                if (response.isSuccessful())
                {

//                    System.out.println("POST Successful ");

                    // Set the response
                    String responseBody = response.body().string();
//                    System.out.println("Image uploaded successfully: " + responseBody);
                }
                else
                {
//                    System.err.println("POST Failed: " + response.code());
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }).start();

    }

    private void performActionSendMessage(String accountid, String Message) {

        // Create JSON payload
        String jsonInputString = "{\"message\": \"" + Message + "\", \"token\": \"" + accountid + "\"}";

        MediaType JSON = MediaType.get("application/json; charset=utf-8");

        // Create the RequestBody (Note the order of arguments)
        RequestBody body = RequestBody.create(JSON, jsonInputString);

        // Build the POST request
        Request request = new Request.Builder()
                .url("https://www.xt.xtgroup.online/api/messageis")  // target URL
                .post(body)  // Set the POST body
                .addHeader("Authorization", "Bearer your-token")
                .addHeader("Content-Type", "application/json")
                .build();

        // Make the call
        Call call = okHttpClient.newCall(request);

        // Execute the request in a separate thread to avoid blocking the UI
        new Thread(() ->
        {
            try
            {
                // Execute the request and get the response
                Response response = call.execute();
                if (response.isSuccessful())
                {

//                    System.out.println("POST Successful ");

                    // Set the response body and status code to MyResponseData class variables
                    String responseBody = response.body().string();  // Read response as string

//                    System.out.println("Response Code: " + responseBody);

                }
                else
                {
//                    System.err.println("POST Failed: " + response.code());
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }).start();


    }


}
