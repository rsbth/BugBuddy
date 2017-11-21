package main;

import converter.Converter;
import scraper.CSVIssueReader;
import scraper.Scraper;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Main {

    private static int maxIssuesToProcess = Integer.MAX_VALUE;

    public static void main(String[] args) {

        processArguments(args);

        ArrayList<FirefoxIssue> firefoxIssues = getIssueData();

        String issueJSONlocation = "../project-issue-data/bugreport.mozilla.firefox/issueJSON/";

        Converter converter = new Converter();

        // Create a JIRA Project json, and write to file
        JiraProject jiraProject = new JiraProject();
        String jiraProjectJson = converter.convertJiraProjectToJiraJSON(jiraProject);
        String jiraProjectJsonFilename = issueJSONlocation + "project.json";

        writeJSONToFile(jiraProjectJson, jiraProjectJsonFilename);

        // Get every unique email in the firefox issues dataset
        Set<String> userEmails = new HashSet<>();
        for (FirefoxIssue issue : firefoxIssues) {
            userEmails.add(issue.getAssigneeEmail());
            userEmails.add(issue.getAssigneeEmail30Days());
            userEmails.add(issue.getReporterEmail());
        }

        // Create Jira users from each email address, and write to a file
        for (String email : userEmails) {
            String userJson = converter.convertEmailAddressToJiraUser(email);
            String userJsonFilename = issueJSONlocation + "users/" + email + ".json";

            writeJSONToFile(userJson, userJsonFilename);

        }

        // Create jira json from every issue, and write to a file
        for (FirefoxIssue firefoxIssue : firefoxIssues) {
            JiraIssue jiraIssue = converter.convertFirefoxIssueToJiraIssue(firefoxIssue);
            String issueJson = converter.convertJiraIssueToJiraJSON(jiraIssue);
            String issueJsonFilename = issueJSONlocation + "issues/" + firefoxIssue.getBugID() + ".json";

            writeJSONToFile(issueJson, issueJsonFilename);
        }



        // Post the project, then all users, then all issues to Jira
        String basicCurlCommand1 = "curl -D- -u admin:admin -X POST --data @" + issueJSONlocation;
        String basicCurlCommand2 = " -H Content-Type:application/json http://localhost:2990/jira/rest/api/2/";

        sendGeneralCurlCommand(basicCurlCommand1 + "project.json" + basicCurlCommand2 + "project");
        for (String email : userEmails) {
            sendGeneralCurlCommand(basicCurlCommand1 + "users/" + email + ".json" + basicCurlCommand2 + "user");
        }

        for (FirefoxIssue firefoxIssue : firefoxIssues) {
            String issueID = sendAddIssueCurlCommand(basicCurlCommand1 + "issues/" + firefoxIssue.getBugID() + ".json" + basicCurlCommand2 + "issue");

            ArrayList<String> comments = firefoxIssue.getComments();
            for (int i = 0; i < comments.size(); i++) {
                String comment = comments.get(i);

                // Convert each comment to JSON
                String commentJson = converter.convertCommentToJiraJSON(comment, issueID);
                String commentJsonFilename = issueJSONlocation + "comments/" + issueID + "-" + i + ".json";
                writeJSONToFile(commentJson, commentJsonFilename);

                // Post to Jira
                sendGeneralCurlCommand(basicCurlCommand1 + "comments/" + issueID + "-" + i + ".json" + basicCurlCommand2 + "issue/" + issueID + "/comment");
            }
        }


    }

    private static void processArguments(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Not Enough Arguments");
        }

        maxIssuesToProcess = Integer.parseInt(args[0]);
    }

    private static void downloadIssueXMLIfRequired(Scraper s, FirefoxIssue issue, int current) {
        boolean downloaded = s.getIssueXML(issue, ("../project-issue-data/bugreport.mozilla.firefox/issueXML/"));
        if (downloaded) {
            System.out.println("Downloaded:\t" + (current + 1) + "/" + maxIssuesToProcess);
        } else {
            System.out.println("Skipped:\t" + (current + 1) + "/" + maxIssuesToProcess);
        }
    }

    private static ArrayList<FirefoxIssue> getIssueData() {
        // Extract issue data from the Bug Database CSV File
        CSVIssueReader reader = new CSVIssueReader();

        List<FirefoxIssue> allIssues = new ArrayList<>();
        try {
            allIssues = reader.readIssuesFromCSV("../project-issue-data/bugreport.mozilla.firefox/mozilla_firefox_bugmeasures.csv");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        // Ensure we only process as many issues as actually exist
        if (allIssues.size() < maxIssuesToProcess) {
            maxIssuesToProcess = allIssues.size();
        }
        ArrayList<FirefoxIssue> issues = new ArrayList<>(allIssues.subList(0, maxIssuesToProcess));

        // Get the comments for each issue, since they
        // aren't provided in the issue data CSV
        Scraper s = new Scraper();

        for (int i = 0; i < maxIssuesToProcess; i++) {
            downloadIssueXMLIfRequired(s, issues.get(i), i);

            ArrayList<String> comments = s.extractIssueComments(issues.get(i));
            issues.get(i).setComments(comments);
        }

        return issues;
    }

    private static void writeJSONToFile(String JSON, String filename) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
            writer.write(JSON);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendGeneralCurlCommand(String curlCommand) {
        try {
            System.out.println(curlCommand);
            Process p = Runtime.getRuntime().exec(curlCommand);
            p.waitFor();

            InputStream stdout = p.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String sendAddIssueCurlCommand(String curlCommand) {
        String successJSON = "";
        try {
            System.out.println(curlCommand);
            Process p = Runtime.getRuntime().exec(curlCommand);
            p.waitFor();

            InputStream stdout = p.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
            String line;
            while ((line = reader.readLine()) != null) {
                successJSON = line;
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
        String issueID = extractIssueIDFromSuccessJSON(successJSON);
        System.out.println("IssueID is:" + issueID);
        return issueID;
    }

    private static String extractIssueIDFromSuccessJSON(String successJSON) {
        System.out.println(successJSON);
        String[] JSONComponents = successJSON.split(",");
        String[] idComponents = JSONComponents[0].split(":");
        String id = idComponents[1];
        id = id.replace("\"", "");

        return id;
    }


}

