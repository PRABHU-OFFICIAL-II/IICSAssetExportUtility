import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

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
            // Build the POST request with the region URL
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + regionUrl + "/ma/api/v2/user/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Status message for response handling
            System.out.println("Processing response...");

            // Check if the response code is not 200
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to log in. HTTP Error Code: " + response.statusCode());
            }

            // Manually parse the JSON response as a String
            String responseBody = response.body();

            // Extract serverUrl
            serverUrl = extractValue(responseBody, "serverUrl");

            // Extract icSessionId
            icSessionId = extractValue(responseBody, "icSessionId");

            // Status message for getting server URL
            System.out.println("Getting server URL: " + serverUrl);

            // Status message for generating session ID
            System.out.println("Generating Session ID: " + icSessionId);

            // Final status message
            System.out.println("Login process completed successfully.");

            // Proceed with asset export
            exportAsset(scanner);

            // After export, handle import into production org
            handleImportToProdOrg(scanner);

        } catch (Exception e) {
            // Error handling status
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
        try {
            // Build the GET request to download the export package
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/public/core/v3/export/" + exportId + "/package"))
                    .header("INFA-SESSION-ID", icSessionId)
                    .GET()
                    .build();

            // Send the request and get the response
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            // Status message for response handling
            System.out.println("Downloading export package...");

            // Check if the response code is not 200
            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to download export package. HTTP Error Code: " + response.statusCode());
            }

            // Save the ZIP file
            java.nio.file.Path path = java.nio.file.Paths.get("export_package.zip");
            java.nio.file.Files.write(path, response.body());
            System.out.println("Export package downloaded successfully as 'export_package.zip'.");

        } catch (Exception e) {
            // Error handling status
            System.out.println("An error occurred while downloading the export package: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Method to check export status by calling
    // <serverUrl>/public/core/v3/export/<id>
    public static void checkExportStatus(String exportId, Scanner scanner) {
        try {
            // Build the GET request to check the export status
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/public/core/v3/export/" + exportId))
                    .header("INFA-SESSION-ID", icSessionId)
                    .GET()
                    .build();

            // Status message for response handling
            System.out.println("Checking export status...");

            // Continuously check the export status until it's "successful"
            while (true) {
                // Send the request and get the response
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // Extract the status from the response
                String responseBody = response.body();
                String status = extractValue(responseBody, "status");
                String state = extractValue(responseBody, "state");

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
        }
    }

    // Method to send the export request to Informatica Cloud
    public static void sendExportRequest(String jsonPayload, Scanner scanner) {
        try {
            // Build the POST request for exporting the asset
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/public/core/v3/export"))
                    .header("Content-Type", "application/json")
                    .header("INFA-SESSION-ID", icSessionId)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // Send the request and get the response
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Status message for response handling
            System.out.println("Processing export response...");

            // Check if the response code is not 200
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to start export. HTTP Error Code: " + response.statusCode());
            }

            // Extract the export ID from the response
            String responseBody = response.body();
            String exportId = extractValue(responseBody, "id");
            System.out.println("Export started successfully. Export ID: " + exportId);

            // Check the export status
            checkExportStatus(exportId, scanner);

        } catch (Exception e) {
            // Error handling status
            System.out.println("An error occurred during export: " + e.getMessage());
            e.printStackTrace();
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
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + regionUrl + "/ma/api/v2/user/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                        "Failed to log in to production org. HTTP Error Code: " + response.statusCode());
            }

            String responseBody = response.body();
            prodServerUrl = extractValue(responseBody, "serverUrl");
            prodIcSessionId = extractValue(responseBody, "icSessionId");

            System.out.println("Logged in to production org successfully.");

        } catch (Exception e) {
            System.out.println("An error occurred during production login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Method to upload the exported package to the production org
    public static void uploadExportedPackageToProd(Scanner scanner) {
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
            byte[] bodyBytes = bodyBuilder.toString().getBytes();

            // Read the file content as bytes
            byte[] fileBytes = Files.readAllBytes(packagePath);

            // Add the closing boundary
            String endBoundary = "\r\n--" + boundary + "--\r\n";
            byte[] endBoundaryBytes = endBoundary.getBytes();

            // Create the final byte array with the correct size
            byte[] finalBody = new byte[bodyBytes.length + fileBytes.length + endBoundaryBytes.length];

            // Copy the header, file content, and closing boundary into the final byte array
            System.arraycopy(bodyBytes, 0, finalBody, 0, bodyBytes.length);
            System.arraycopy(fileBytes, 0, finalBody, bodyBytes.length, fileBytes.length);
            System.arraycopy(endBoundaryBytes, 0, finalBody, bodyBytes.length + fileBytes.length,
                    endBoundaryBytes.length);

            // Build the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(prodServerUrl + "/public/core/v3/import/package"))
                    .header("INFA-SESSION-ID", prodIcSessionId)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(finalBody))
                    .build();

            // Send the HTTP request
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Check the response status
            if (response.statusCode() != 200) {
                System.out.println("HTTP Error: " + response.statusCode());
                System.out.println("Response Body: " + response.body());
                throw new RuntimeException("Failed to upload the package. HTTP Error Code: " + response.statusCode()
                        + ". Response Body: " + response.body());
            }

            // Parse the response for the import job ID
            String responseBody = response.body();
            String importJobId = extractValue(responseBody, "jobId");
            System.out.println("Package uploaded successfully. Import Job ID: " + importJobId);

            // Check the import status
            startUploadJob(importJobId, prodIcSessionId, scanner);

        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred during package upload: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Method to start the upload job
    public static void startUploadJob(String jobId, String sessionId, Scanner scanner) {
        try {
            // Construct the URL for the import job start API
            String url = prodServerUrl + "/public/core/v3/import/" + jobId;
            System.out.println("Starting import job at URL: " + url);

            // Create the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("INFA-SESSION-ID", sessionId)
                    .header("Content-Type", "application/json") // Content-Type header as per your cURL command
                    .POST(HttpRequest.BodyPublishers.noBody()) // Empty POST body
                    .build();

            // Send the HTTP request
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Check if the request was successful
            if (response.statusCode() != 200) {
                System.out.println("HTTP Error: " + response.statusCode());
                System.out.println("Response Body: " + response.body());
                throw new RuntimeException("Failed to start the import job. HTTP Error Code: " + response.statusCode());
            }

            // Output success message
            System.out.println("Import job started successfully.");

            checkImportStatus(jobId, scanner);

        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred while starting the import job: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void logout(String sessionId, String username, String password) {
        try {
            // Logout URL
            String logoutUrl = "https://dm-us.informaticacloud.com/ma/api/v2/user/logout";

            // Create the request body
            String requestBody = "{ \"@type\": \"login\", \"username\": \"" + username + "\", \"password\": \""
                    + password + "\" }";

            // Create the HTTP request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(logoutUrl))
                    .header("icSessionId", sessionId)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody)) // Request body with username and password
                    .build();

            // Send the HTTP request
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Check if the request was successful
            if (response.statusCode() != 200) {
                System.out.println("Failed to log out. HTTP Error Code: " + response.statusCode());
                System.out.println("Response Body: " + response.body());
                throw new RuntimeException("Logout failed for session: " + sessionId);
            }

            // Success
            System.out.println("Successfully logged out session ID: " + sessionId);

        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred during logout: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Method to check the import status
    public static void checkImportStatus(String importJobId, Scanner scanner) {
        try {
            String importStatusUrl = prodServerUrl + "/public/core/v3/import/" + importJobId;

            while (true) {
                // Build the request to check the import status
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(importStatusUrl))
                        .header("INFA-SESSION-ID", prodIcSessionId)
                        .GET()
                        .build();

                // Send the request
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                // Check the response status
                if (response.statusCode() == 200) {
                    String responseBody = response.body();
                    String state = extractValue(responseBody, "state");
                    // String message = extractValue(responseBody, "message");

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
                    System.out.println("Failed to check import status. HTTP Error Code: " + response.statusCode());
                }

                // Wait for some time before the next status check
                Thread.sleep(5000);
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("An error occurred while checking the import status: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
