package com.automated.jira;

import java.io.IOException;

import com.automated.jira.jiraautomated;

public class JiraMain {
	
	public static void main(String[] args) throws IOException {
		
		// TODO Auto-generated method stub
		String summary = "Test JIRA Automation workflow Review";
		jiraautomated jAuto = new jiraautomated();
		jAuto.createJIRAConnectivity();
		jAuto.createJIRA(summary);
		String b = jAuto.searchJIRA(summary);
		jAuto.addIssueAttachment(b, "C:/Users/murugch/Desktop/Jira_Error.jpg");
		jAuto.setIssueTransition(b, "Start Progress");
		jAuto.setIssueTransition(b, "Deferred");
		jAuto.setIssueTransition(b, "Close");
		jAuto.setIssueTransition(b, "Reopen");
		jAuto.closeJIRAConnectivity();
	}
}
