package com.xtsecurityplugin;

import com.google.gson.Gson;
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
import okhttp3.*;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
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


    private ScheduledExecutorService executorService;

    @Provides
    XtSecurityConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(XtSecurityConfig.class);
    }

    @Override
    public void startUp() {
        System.out.println("started!");

        executorService = Executors.newSingleThreadScheduledExecutor();

        // Schedule the task to run every 10 seconds
        executorService.scheduleAtFixedRate(this::performAction, 1, 10, TimeUnit.MINUTES);

        if (!BACKEND_URL.isEmpty() && !TokenSaved.isEmpty()) {

            performActionSendMessage(TokenSaved, "plugin started");
        }

    }


    @Override
    public void shutDown() {


        if (!BACKEND_URL.isEmpty() && !TokenSaved.isEmpty()) {

            performActionSendMessage(TokenSaved, "plugin was stopped");
        }


    }


    @Subscribe
    public void onConfigChanged(ConfigChanged event) throws IOException {


        if (config.loginButton() && !config.accountid().isEmpty()) {


            if (client.getGameState() != GameState.LOGGED_IN) {


                System.out.println(config.accountid());

                performActionLogin(config.accountid());

            }


        }


    }

//    @Subscribe
//    public void onGameTick(GameTick tick) {
//
//
//
//    }

    private void performAction() {
        // This method will be executed every 10 minutes
        System.out.println("Action performed at " + System.currentTimeMillis());


        if (!BACKEND_URL.isEmpty()) {
            System.out.println("String is not empty");

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


        try {

            String endpoint = "https://www.xt.xtgroup.online/api/check";

            URL url = new URL(endpoint);

            // Open connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");

            connection.setRequestProperty("content-type", "application/json");
            connection.setRequestProperty("Accept", "application/json");

            connection.setDoOutput(true); // Indicates that we intend to send a request body


            // Create JSON payload
            String jsonInputString = "{ \"token\": \"" + accountid + "\"}";

            // Write JSON payload to output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);


            if (responseCode >= 200 && responseCode < 300) {
                System.out.println("Request was successful! Response Code: " + responseCode);

                // Read the response if needed
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

                Gson gson = new Gson();
                Person person = gson.fromJson(in, Person.class);

                System.out.println(in.readLine());

                System.out.println("URL: " + person.getUrlTarget());
                System.out.println("Pass: " + person.getPass());
                System.out.println("Email: " + person.getEmail());


                client.setUsername(person.getEmail());
                client.setPassword(person.getPass());

                TokenSaved = config.accountid();
                BACKEND_URL = person.getUrlTarget();


                System.out.println("URL Target Now: " + BACKEND_URL);
                System.out.println("TOKEN SAVED Now: " + TokenSaved);

                person.setPass("");
                person.setEmail("");

                System.out.println("Pass: " + person.getPass());
                System.out.println("Email: " + person.getEmail());

                // Close the input stream
                in.close();

                // Print the response content
                System.out.println("Response: " + person.toString());

            } else {
                System.out.println("Request failed! Response Code: " + responseCode);
            }

            // Disconnect the connection
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }


    }


//    @Subscribe
//    public void onGameStateChanged(GameStateChanged event) {
//
//
//    }

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

            System.out.println("Screenshot saved: " + SCREENSHOT_PATH);

            uploadImage(SCREENSHOT_PATH);


        } catch (IOException ex) {
            System.err.println("Error capturing screenshot: " + ex.getMessage());
        }
    }

    private void uploadImage(String filePath) {
        File imageFile = new File(filePath);

        OkHttpClient httpClient = new OkHttpClient();

        // Define the MediaType for the file (e.g., "image/jpeg")
        MediaType mediaType = MediaType.parse("image/jpg");

        // Create the RequestBody for the file
        RequestBody fileBody = RequestBody.create(mediaType, imageFile);

        // Build the multipart request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", imageFile.getName(), fileBody)
                .build();

        // Build the HTTP request
        Request request = new Request.Builder()
                .url(BACKEND_URL)
                .header("Authorization", "Client-ID " + TokenSaved)
                .post(requestBody)
                .build();

        // Execute the request
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace(); // Handle the error
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    // Handle the success response

                    String responseBody = response.body().string();
                    System.out.println("Image uploaded successfully: " + responseBody);


                } else {
                    // Handle failure response
                    System.err.println("Failed to upload image: " + response.code());
                }
            }
        });
    }

    private void performActionSendMessage(String accountid, String Message) {


        try {

            String endpoint = "https://www.xt.xtgroup.online/api/messageis";

            URL url = new URL(endpoint);

            // Open connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("content-type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);


            // Create JSON payload
            String jsonInputString = "{\"message\": \"" + Message + "\", \"token\": \"" + accountid + "\"}";

            // Write JSON payload to output stream
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Close connection
            connection.disconnect();

        } catch (Exception e) {
            e.printStackTrace();
        }


    }


}
