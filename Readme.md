# CrossProdAssetExportUtility

**CrossProdAssetExportUtility is a Java-based tool that helps automate the process of exporting, uploading, and importing asset packages between Informatica Cloud non-production and production environments. The tool interacts with the Informatica Cloud REST API to manage sessions, upload packages, monitor import status, and log out of both environments upon completion.**

## Features :-

1. Exports asset packages from non-production to production.
2. Uploads the exported package to the production environment.
3. Initiates and monitors the import job status.
4. Logs out from both non-production and production environments.
5. Designed to be interactive with prompts for credentials and server details.
6. Displays real-time status updates for the package upload and import job.

## Prerequisites :-

1. **Java 11 or above** - Ensure you have Java installed on your machine.

2. **Informatica Cloud REST API Access** - Make sure you have the necessary permissions and credentials for using the Informatica Cloud REST API.

## Set Up :-

1. **Clone the Repository** :-

```bash
git clone https://github.com/PRABHU-OFFICIAL-II/IICSAssetExportUtility.git
```

2. **Navigate to the project directory** :- 

```bash
cd IICSAssetExportUtility
```

3. **Create the class file** :- 

```bash
javac CrossProdAssetExportUtility.java
```

4. **Execute the class file** :- 

```bash
java CrossProdAssetExportUtility
```

## Usage :-

1. **When you run the tool, it will interactively request the following :-**
    
    a. Non-Production Organization Login:
    
    i. Username
    ii. Password
    iii. Region URL (e.g., dm-us.informaticacloud.com)
    
    b. Production Organization Login:
    
    i. Username
    ii. Password
    iii. Region URL (e.g., dm-us.informaticacloud.com)

2. **The tool will proceed with the following steps :-**

    a. Login to the non-production and production orgs using the provided credentials.
    b. Upload the exported package to the production org.
    c. Start the import job using the API.
    d. Monitor the import job status until it reaches a final state (SUCCESSFUL or FAILED).
    e. Log out from both the non-production and production environments.

## Error Handling :-

1. If the upload, import start, or status check fails, the tool will display the corresponding HTTP error code and response body.

2. The tool handles common exceptions like IOException and InterruptedException and provides error messages with stack traces for debugging purposes.

**Currently working on improving the dunctionality t allow for multiple Assest Export and import.**
**Open to any contributions from your side.**