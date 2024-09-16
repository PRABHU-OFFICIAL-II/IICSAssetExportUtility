import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

@SuppressWarnings("deprecation")
public class CrossProdAssetExportUtility {

    // Global variables for server URL and session ID
    private static String serverUrl;
    private static String icSessionId;
    private static String prodServerUrl;
    private static String prodIcSessionId;
    private static String username;
    private static String password;
    private static String prodUsername;
    private static String prodPassword;

    public static void main(String[] args) {
        // Welcome message
        System.out.println("======= Welcome to the CrossProd Asset Export Utility =======");
        System.out.println(
                "======= Please provide the necessary details for authentication into NON - PROD Environment =======");

        // Create Scanner to get user input
        Scanner scanner = new Scanner(System.in);

        // Get the region URL from the user
        System.out.print("Enter Region URL (e.g., dm-us.informaticacloud.com): ");
        String regionUrl = scanner.nextLine();

        // Get the credentials from the user
        System.out.print("Enter Username: ");
        username = scanner.nextLine();

        System.out.print("Enter Password: ");
        password = scanner.nextLine();

        // Status message for parsing input
        System.out.println("Parsing input credentials...");

        // Prepare the JSON payload
        String jsonPayload = "{ \"username\": \"" + username + "\", \"password\": \"" + password + "\" }";

        // Status message for sending request
        System.out.println("Sending login request to Informatica Cloud...");

        // Send the login request and store session details
        sendLoginRequest(regionUrl, jsonPayload, scanner);
    }

    public static void sendLoginRequest(String regionUrl, String jsonPayload, Scanner scanner) {
        try {
            // Build the URL for the POST request
            URL url = new URL("https://" + regionUrl + "/ma/api/v2/user/login");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Set request method to POST
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Send the JSON payload
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonPayload.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            // Read the response
            int responseCode = connection.getResponseCode();
            System.out.println("Processing response...");

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed to log in. HTTP Error Code: " + responseCode);
            }

            // Get response body
            StringBuilder responseBody = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    responseBody.append(responseLine.trim());
                }
            }

            // Manually parse the JSON response as a String
            String response = responseBody.toString();

            // Extract serverUrl
            serverUrl = extractValue(response, "serverUrl");

            // Extract icSessionId
            icSessionId = extractValue(response, "icSessionId");

            // Status messages
            System.out.println("Getting server URL: " + serverUrl);
            System.out.println("Generating Session ID: " + icSessionId);

            // Final status message
            System.out.println("Login process completed successfully.");

            // Proceed with asset export
            exportAsset(scanner);

            // After export, handle import into production org
            handleImportToProdOrg(scanner);

        } catch (Exception e) {
            // Error handling
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper function to extract values from the JSON response string
    public static String extractValue(String jsonString, String key) {
        String keyWithQuotes = "\"" + key + "\":";
        int startIndex = jsonString.indexOf(keyWithQuotes);
        if (startIndex == -1) {
            return null;
        }
        startIndex += keyWithQuotes.length();
        while (jsonString.charAt(startIndex) == ' ' || jsonString.charAt(startIndex) == '\"') {
            startIndex++;
        }
        int endIndex = jsonString.indexOf('"', startIndex);
        return jsonString.substring(startIndex, endIndex);
    }

    // Method to ask for asset ID and dependencies, and start export process
    public static void exportAsset(Scanner scanner) {
        System.out.println("Proceeding with asset export...");

        // Ask user for the asset ID to export
        System.out.print("Enter Asset ID to export: ");
        String assetId = scanner.nextLine();

        // Ask user whether to include dependencies (y/n)
        System.out.print("Include dependencies? (y/n): ");
        String includeDependenciesInput = scanner.nextLine();
        boolean includeDependencies = includeDependenciesInput.equalsIgnoreCase("y");

        // Build the export request
        String jsonPayload = "{ \"name\": \"UtilityExport\", " +
                "\"objects\": [{ " +
                "\"id\": \"" + assetId + "\", " +
                "\"includeDependencies\": " + includeDependencies + " }] }";

        // Send the export request
        sendExportRequest(jsonPayload, scanner);
    }

    // Method to download the export package as a zip file
    public static void downloadExportPackage(String exportId) {
        HttpURLConnection connection = null;
        BufferedInputStream in = null;
        FileOutputStream fileOutputStream = null;

        try {
            // Build the URL for the GET request
            URL url = new URL(serverUrl + "/public/core/v3/export/" + exportId + "/package");
            connection = (HttpURLConnection) url.openConnection();
            
            // Set request method to GET
            connection.setRequestMethod("GET");
            connection.setRequestProperty("INFA-SESSION-ID", icSessionId);
            connection.setDoInput(true);

            // Send the request
            connection.connect();

            // Check if the response code is 200
            int responseCode = connection.getResponseCode();
            System.out.println("Downloading export package...");
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed to download export package. HTTP Error Code: " + responseCode);
            }

            // Read the response as a byte stream
            in = new BufferedInputStream(connection.getInputStream());
            java.nio.file.Path path = java.nio.file.Paths.get("export_package.zip");
            fileOutputStream = new FileOutputStream(path.toFile());

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }

            System.out.println("Export package downloaded successfully as 'export_package.zip'.");

        } catch (IOException e) {
            // Error handling status
            System.out.println("An error occurred while downloading the export package: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (in != null) in.close();
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException e) {
                System.out.println("An error occurred while closing resources: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }

    // Method to check export status by calling
    @SuppressWarnings("resource")
    public static void checkExportStatus(String exportId, Scanner scanner) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            // Build the URL for the GET request
            URL url = new URL(serverUrl + "/public/core/v3/export/" + exportId);
            connection = (HttpURLConnection) url.openConnection();
            
            // Set request method to GET
            connection.setRequestMethod("GET");
            connection.setRequestProperty("INFA-SESSION-ID", icSessionId);
            connection.setDoInput(true);

            System.out.println("Checking export status...");

            while (true) {
                // Send the request
                connection.connect();

                // Get response code
                int responseCode = connection.getResponseCode();
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new RuntimeException("Failed to check export status. HTTP Error Code: " + responseCode);
                }

                // Read the response
                reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }

                // Extract the status from the response
                String responseStr = responseBody.toString();
                String status = extractValue(responseStr, "status");
                String state = extractValue(responseStr, "state");

                if ("SUCCESSFUL".equalsIgnoreCase(state)) {
                    System.out.println("Export successful!");

                    // Download the export package
                    downloadExportPackage(exportId);
                    break;
                } else if ("FAILED".equalsIgnoreCase(state)) {
                    throw new RuntimeException("Export failed.");
                } else {
                    // Status is still pending, wait for a while before checking again
                    System.out.println("Export is in progress. Current status: " + status);
                    Thread.sleep(5000); // Wait for 5 seconds before checking again
                }
            }

        } catch (Exception e) {
            // Error handling status
            System.out.println("An error occurred while checking export status: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (reader != null) reader.close();
            } catch (Exception e) {
                System.out.println("An error occurred while closing resources: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }

    // Method to send the export request to Informatica Cloud
    public static void sendExportRequest(String jsonPayload, Scanner scanner) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        OutputStream outputStream = null;

        try {
            // Build the URL for the POST request
            URL url = new URL(serverUrl + "/public/core/v3/export");
            connection = (HttpURLConnection) url.openConnection();
            
            // Set request method to POST
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("INFA-SESSION-ID", icSessionId);
            connection.setDoOutput(true);

            // Send the JSON payload
            outputStream = connection.getOutputStream();
            byte[] input = jsonPayload.getBytes("utf-8");
            outputStream.write(input, 0, input.length);

            // Read the response
            int responseCode = connection.getResponseCode();
            System.out.println("Processing export response...");

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed to start export. HTTP Error Code: " + responseCode);
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }

            // Extract the export ID from the response
            String responseStr = responseBody.toString();
            String exportId = extractValue(responseStr, "id");
            System.out.println("Export started successfully. Export ID: " + exportId);

            // Check the export status
            checkExportStatus(exportId, scanner);

        } catch (Exception e) {
            // Error handling
            System.out.println("An error occurred during export: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (outputStream != null) outputStream.close();
                if (reader != null) reader.close();
            } catch (Exception e) {
                System.out.println("An error occurred while closing resources: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }

    // Method to handle the import process into the production org
    public static void handleImportToProdOrg(Scanner scanner) {
        System.out.println(
                "======= Please provide the necessary details for authentication into PROD Environment =======");
        System.out.println("======= Initiating import to production organization =======");

        // Prompt for production credentials
        System.out.print("Enter Production Region URL: ");
        String prodRegionUrl = scanner.nextLine();

        System.out.print("Enter Production Username: ");
        prodUsername = scanner.nextLine();

        System.out.print("Enter Production Password: ");
        prodPassword = scanner.nextLine();

        // Prepare the login payload for the production org
        String prodLoginPayload = "{ \"username\": \"" + prodUsername + "\", \"password\": \"" + prodPassword + "\" }";

        // Log in to production org
        sendProdLoginRequest(prodRegionUrl, prodLoginPayload, scanner);

        // Upload the exported package to production
        uploadExportedPackageToProd(scanner);
    }

    // Method to log in to the production org
    public static void sendProdLoginRequest(String regionUrl, String jsonPayload, Scanner scanner) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        OutputStream outputStream = null;

        try {
            // Build the URL for the POST request
            URL url = new URL("https://" + regionUrl + "/ma/api/v2/user/login");
            connection = (HttpURLConnection) url.openConnection();
            
            // Set request method to POST
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // Send the JSON payload
            outputStream = connection.getOutputStream();
            byte[] input = jsonPayload.getBytes("utf-8");
            outputStream.write(input, 0, input.length);

            // Read the response
            int responseCode = connection.getResponseCode();

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException(
                        "Failed to log in to production org. HTTP Error Code: " + responseCode);
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }

            // Extract serverUrl and icSessionId from the response
            String responseStr = responseBody.toString();
            prodServerUrl = extractValue(responseStr, "serverUrl");
            prodIcSessionId = extractValue(responseStr, "icSessionId");

            System.out.println("Logged in to production org successfully.");

        } catch (Exception e) {
            System.out.println("An error occurred during production login: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (outputStream != null) outputStream.close();
                if (reader != null) reader.close();
            } catch (Exception e) {
                System.out.println("An error occurred while closing resources: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }

    // Method to upload the exported package to the production org
    public static void uploadExportedPackageToProd(Scanner scanner) {
        HttpURLConnection connection = null;
        OutputStream outputStream = null;
        BufferedReader reader = null;

        try {
            // Path to the exported package file on local machine
            Path packagePath = Paths.get("export_package.zip");

            // Ensure the file exists
            if (!Files.exists(packagePath)) {
                throw new RuntimeException("Package file not found: " + packagePath.toAbsolutePath());
            }

            System.out.println("Uploading export package to production...");
            System.out.println("Using URL: " + prodServerUrl + "/public/core/v3/import/package");
            System.out.println("Using INFA-SESSION-ID: " + prodIcSessionId);

            // Create a boundary
            String boundary = "Boundary-" + System.currentTimeMillis();

            // Create the multipart body header
            StringBuilder bodyBuilder = new StringBuilder();
            bodyBuilder.append("--").append(boundary).append("\r\n");
            bodyBuilder.append("Content-Disposition: form-data; name=\"package\"; filename=\"")
                    .append(packagePath.getFileName()).append("\"\r\n");
            bodyBuilder.append("Content-Type: application/zip\r\n\r\n");

            // Convert the header part to bytes
            byte[] bodyBytes = bodyBuilder.toString().getBytes("utf-8");

            // Read the file content as bytes
            byte[] fileBytes = Files.readAllBytes(packagePath);

            // Add the closing boundary
            String endBoundary = "\r\n--" + boundary + "--\r\n";
            byte[] endBoundaryBytes = endBoundary.getBytes("utf-8");

            // Create the final byte array with the correct size
            byte[] finalBody = new byte[bodyBytes.length + fileBytes.length + endBoundaryBytes.length];

            // Copy the header, file content, and closing boundary into the final byte array
            System.arraycopy(bodyBytes, 0, finalBody, 0, bodyBytes.length);
            System.arraycopy(fileBytes, 0, finalBody, bodyBytes.length, fileBytes.length);
            System.arraycopy(endBoundaryBytes, 0, finalBody, bodyBytes.length + fileBytes.length,
                    endBoundaryBytes.length);

            // Build the HTTP request
            URL url = new URL(prodServerUrl + "/public/core/v3/import/package");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("INFA-SESSION-ID", prodIcSessionId);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setDoOutput(true);

            // Send the request body
            outputStream = connection.getOutputStream();
            outputStream.write(finalBody);
            outputStream.flush();

            // Read the response
            int responseCode = connection.getResponseCode();
            System.out.println("HTTP Response Code: " + responseCode);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
                throw new RuntimeException("Failed to upload the package. HTTP Error Code: " + responseCode
                        + ". Response Body: " + responseBody.toString());
            }

            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }

            // Parse the response for the import job ID
            String responseStr = responseBody.toString();
            String importJobId = extractValue(responseStr, "jobId");
            System.out.println("Package uploaded successfully. Import Job ID: " + importJobId);

            // Check the import status
            startUploadJob(importJobId, prodIcSessionId, scanner);

        } catch (IOException e) {
            System.out.println("An error occurred during package upload: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (outputStream != null) outputStream.close();
                if (reader != null) reader.close();
            } catch (IOException e) {
                System.out.println("An error occurred while closing resources: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }

    // Method to start the upload job
    public static void startUploadJob(String jobId, String sessionId, Scanner scanner) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            // Construct the URL for the import job start API
            String url = prodServerUrl + "/public/core/v3/import/" + jobId;
            System.out.println("Starting import job at URL: " + url);

            // Create the HTTP request
            URL requestUrl = new URL(url);
            connection = (HttpURLConnection) requestUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("INFA-SESSION-ID", sessionId);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true); // Indicates that this is a POST request

            // Send the HTTP request
            int responseCode = connection.getResponseCode();
            System.out.println("HTTP Response Code: " + responseCode);

            // Check if the request was successful
            if (responseCode != HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
                throw new RuntimeException("Failed to start the import job. HTTP Error Code: " + responseCode
                        + ". Response Body: " + responseBody.toString());
            }

            // Read the response
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }

            // Output success message
            System.out.println("Import job started successfully.");
            System.out.println("Response: " + responseBody.toString());

            // Proceed to check the import status
            checkImportStatus(jobId, scanner);

        } catch (IOException e) {
            System.out.println("An error occurred while starting the import job: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                System.out.println("An error occurred while closing the reader: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }

    public static void logout(String sessionId, String username, String password) {
        HttpURLConnection connection = null;
        OutputStream outputStream = null;
        BufferedReader reader = null;

        try {
            // Logout URL
            String logoutUrl = "https://dm-us.informaticacloud.com/ma/api/v2/user/logout";

            // Create the request body
            String requestBody = "{ \"@type\": \"login\", \"username\": \"" + username + "\", \"password\": \"" + password + "\" }";

            // Create the HTTP request
            URL url = new URL(logoutUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("icSessionId", sessionId);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true); // Indicates that this is a POST request

            // Send the request body
            outputStream = connection.getOutputStream();
            outputStream.write(requestBody.getBytes("UTF-8"));
            outputStream.flush();

            // Get the response
            int responseCode = connection.getResponseCode();
            System.out.println("HTTP Response Code: " + responseCode);

            // Check if the request was successful
            if (responseCode != HttpURLConnection.HTTP_OK) {
                reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
                StringBuilder responseBody = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line);
                }
                throw new RuntimeException("Failed to log out. HTTP Error Code: " + responseCode
                        + ". Response Body: " + responseBody.toString());
            }

            // Read the successful response
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            StringBuilder responseBody = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBody.append(line);
            }

            // Success
            System.out.println("Successfully logged out session ID: " + sessionId);
            System.out.println("Response: " + responseBody.toString());

        } catch (IOException e) {
            System.out.println("An error occurred during logout: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (outputStream != null) outputStream.close();
                if (reader != null) reader.close();
            } catch (IOException e) {
                System.out.println("An error occurred while closing resources: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }

    // Method to check the import status
    public static void checkImportStatus(String importJobId, Scanner scanner) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            String importStatusUrl = prodServerUrl + "/public/core/v3/import/" + importJobId;

            while (true) {
                // Build the request to check the import status
                URL url = new URL(importStatusUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("INFA-SESSION-ID", prodIcSessionId);

                // Send the request
                int responseCode = connection.getResponseCode();
                System.out.println("HTTP Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                    StringBuilder responseBody = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }

                    // Extract the state from the response
                    String responseBodyString = responseBody.toString();
                    String state = extractValue(responseBodyString, "state");

                    System.out.println("Import Status: " + state);

                    // Break the loop if the import is completed
                    if ("SUCCESSFUL".equalsIgnoreCase(state) || "FAILED".equalsIgnoreCase(state)) {

                        // Logout from non-production
                        System.out.println("Logging out of NON - PROD Environment...");
                        logout(icSessionId, username, password);

                        // Logout from production
                        System.out.println("Logging out of PROD Environment...");
                        logout(prodIcSessionId, prodUsername, prodPassword);
                        break;
                    }

                } else {
                    System.out.println("Failed to check import status. HTTP Error Code: " + responseCode);
                }

                // Wait for some time before the next status check
                Thread.sleep(5000);
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred while checking the import status: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Clean up resources
            try {
                if (reader != null) reader.close();
            } catch (IOException e) {
                System.out.println("An error occurred while closing resources: " + e.getMessage());
            }
            if (connection != null) connection.disconnect();
        }
    }
}
