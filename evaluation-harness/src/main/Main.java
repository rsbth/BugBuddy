package main;

import com.google.gson.Gson;
import scraper.CSVIssueReader;
import scraper.Scraper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

class Main {

    private static int maxIssuesToProcess = Integer.MAX_VALUE;

    public static void main(String[] args) {

        extractMaxIssuesToProcessFromArguments(args);

        ArrayList<FirefoxIssue> issues = getIssueData();

        // Convert issues into JIRA classes
        JiraProject jiraProject = new JiraProject();
        Gson gson = new Gson();
        String jiraProjectJsonFilename = "/home/stephen/bug_buddy_jira_plugin/project-issue-data/bugreport.mozilla.firefox/issueJSON/project.json";

        try {
            File jiraProjectJsonFile = new File(jiraProjectJsonFilename);
            BufferedWriter writer = new BufferedWriter(new FileWriter(jiraProjectJsonFilename));
            writer.write(gson.toJson(jiraProject));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }




    }

    private static void extractMaxIssuesToProcessFromArguments(String[] args) {
        if (args.length > 0) {
            maxIssuesToProcess = Integer.parseInt(args[0]);
        }
    }

    private static void downloadIssueXMLIfRequired(Scraper s, FirefoxIssue issue, int current) {
        boolean downloaded = s.getIssueXML(issue);
        if (downloaded) {
            System.out.println("Downloaded:\t" + (current + 1) + "/" + maxIssuesToProcess);
        } else {
            System.out.println("Skipped:\t" + (current + 1) + "/" + maxIssuesToProcess);
        }
    }

    private static ArrayList<FirefoxIssue> getIssueData() {
        // Extract issue data from the Bug Database CSV File
        ArrayList<FirefoxIssue> issues;
        CSVIssueReader reader = new CSVIssueReader();
        issues = reader.readIssuesFromCSV("/home/stephen/bug_buddy_jira_plugin/project-issue-data/bugreport.mozilla.firefox/mozilla_firefox_bugmeasures.csv");

        // Ensure we only process as many issues as actually exist
        if (issues.size() < maxIssuesToProcess) {
            maxIssuesToProcess = issues.size();
        }

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
}
