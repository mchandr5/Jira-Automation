package com.automated.jira;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.apache.commons.codec.binary.Base64;
import org.codehaus.jettison.json.JSONArray;

import com.atlassian.jira.rest.client.api.ComponentRestClient;
import com.atlassian.jira.rest.client.api.GetCreateIssueMetadataOptions;
import com.atlassian.jira.rest.client.api.GetCreateIssueMetadataOptionsBuilder;
import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.MetadataRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.BasicPriority;
import com.atlassian.jira.rest.client.api.domain.CimFieldInfo;
import com.atlassian.jira.rest.client.api.domain.CimIssueType;
import com.atlassian.jira.rest.client.api.domain.CimProject;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.CustomFieldOption;
import com.atlassian.jira.rest.client.api.domain.Field;
import com.atlassian.jira.rest.client.api.domain.FieldSchema;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.Priority;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.SecurityLevel;
import com.atlassian.jira.rest.client.api.domain.StandardOperation;
import com.atlassian.jira.rest.client.api.domain.Transition;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInputBuilder;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClient;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.atlassian.util.concurrent.Promise;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

@SuppressWarnings("all")
public class jiraautomated {
	
	private static URI jiraServerUri = URI.create("https://onejiratest-c.ebiz.verizon.com");
	private static AsynchronousJiraRestClient restClient = null;
	private static AsynchronousJiraRestClientFactory factory = null;
	private static IssueRestClient issueClient = null;
	private static IssueInputBuilder issueBuild = null;
	private static SearchRestClient searchClient = null;
	private static MetadataRestClient metaClient = null;
	
	public static List<String> requiredFields = new ArrayList<>();
	public static ListMultimap<String, String>allowableFieldVals = ArrayListMultimap.create();
	public static Map<String, String> projectFieldsType = new HashMap<String, String>();
	public static Map<String, String> projectFieldAttrib = new HashMap<String, String>();
	public static Map<String, String> projectCustFieldType = new HashMap<String, String>();
	public static Map<String, String> jiraFields = new HashMap<String, String>();
	public static Map<String, String> issueFieldsList = new HashMap<String, String>();
	
	private Issue issue = null;
	private static String projKey = null; 
	
	public void createJIRAConnectivity() {
		try {
			getProjectJIRAFields();
			projKey = jiraFields.get("Project");
			factory = new AsynchronousJiraRestClientFactory();
			String userName = jiraFields.get("User");
			String password = decodeValues(jiraFields.get("Pass"));
			restClient = (AsynchronousJiraRestClient) factory.createWithBasicHttpAuthentication(jiraServerUri, userName, password);
			if (restClient!=null) {
				System.out.println("JIRA connectivity established successfully!");
				issueClient = restClient.getIssueClient();
				searchClient = restClient.getSearchClient();
				metaClient = restClient.getMetadataClient();
			} else {
				System.out.println("Error in JIRA Connectivity");
			}
			issueBuild = new IssueInputBuilder(projKey, 1L);
			getIssueFields();
		} catch (Exception e) {
			System.out.println("Error in JIRA Connectivity :" + e.getMessage());
		}
	}
	
	
	/**
	 * 	@author murugch
	 *  @description closing the JIRA Client
	 *  @param
	 */
	
	public void closeJIRAConnectivity() {
		try {
			if (restClient!=null) {
				restClient.close();
				System.out.println("JIRA Client connectivity is closed!");
			}
				
		} catch (Exception e) {
			System.out.println("Error in closing the JIRA Client connectivity!");
		}
	}
	
		
	/**
	 * @author murugch
	 * @description Setting the priority for an issue
	 * @param priority
	 */
	
	private void setIssuePriority() {
		try {
			String priority = jiraFields.get("Priority");
			Promise<Iterable<Priority>> priorities = metaClient.getPriorities();
			Iterable<Priority> iterPrior = priorities.claim();
			for(Priority prior: iterPrior) {
				String pName = prior.getName();
				if (pName.equalsIgnoreCase(priority.toLowerCase())) {
					issueBuild.setPriority(prior);
					break;
				}
			}
		} catch (Exception e) {
			System.out.println("Error in setting priority!");
		}
	}
	
	
	/**
	 * @author murugch
	 * @description get the list of fields associated with an issuetype for a project
	 * @param Project Key 
	 * @return fieldMap <FieldName, FieldID>
	 */
	
	private void getIssueFields() {
		
		String fieldName = null;
		try {
			Map<String, CimFieldInfo> fieldInfo = null;
			Long issueID = getIssueTypeID();
			GetCreateIssueMetadataOptions metaOpts = new GetCreateIssueMetadataOptionsBuilder().withExpandedIssueTypesFields().withIssueTypeIds(issueID).withProjectKeys(projKey).build();
			Promise<Iterable<CimProject>> cProjs = issueClient.getCreateIssueMetadata(metaOpts);
			Iterable<CimProject> cimProjs = cProjs.claim();
			
			for (CimProject proj : cimProjs) {
				Iterable <CimIssueType> issueTypes = proj.getIssueTypes();
				for(CimIssueType type: issueTypes){
	                if (type.getId().equals(issueID)) {
	                	fieldInfo = type.getFields();
	                	break;
	                }
	            }
			}
			
			Set<String> fldKeys = fieldInfo.keySet();
			for (String key : fldKeys) {
				fieldName = fieldInfo.get(key).getName();
				String fieldID = fieldInfo.get(key).getId();
				boolean isReq = fieldInfo.get(key).isRequired();
				String fieldType = fieldInfo.get(key).getSchema().getType();
				String fieldCust = fieldInfo.get(key).getSchema().getCustom();
				projectFieldsType.put(fieldName, fieldType);
				projectCustFieldType.put(fieldName, fieldCust);
				if (!fieldName.equalsIgnoreCase("Issue Type")) {
					Iterable allowVals = fieldInfo.get(key).getAllowedValues();
					if (allowVals !=null) {
						for (Object allow : allowVals) {
							getFieldAllowableValues(allow, fieldName);
						}
					}
					issueFieldsList.put(fieldName, fieldID);
					if (isReq) {
						requiredFields.add(fieldName);
					}
				}
			}
		} catch (Exception e) {
			System.out.println("Error in the getting the field details :" + e.getMessage() + ": " + fieldName);
		}
	}
	
	
	private void getFieldAllowableValues(Object allow, String fieldName) {
		try {
			String classType = allow.getClass().getSimpleName();
			switch (classType) {
				case "JSONObject":
					Map values = parseJSON((JSONObject)allow);
					if (values.containsKey("name")) {
						allowableFieldVals.put(fieldName, values.get("name").toString());
						projectFieldAttrib.put(fieldName, "name");
					} else if (values.containsKey("value")) {
						allowableFieldVals.put(fieldName, values.get("value").toString());
						projectFieldAttrib.put(fieldName, "value");
					}
					break;
				case "BasicComponent":
					String compName = ((BasicComponent)allow).getName();
					allowableFieldVals.put(fieldName, compName);
					projectFieldAttrib.put(fieldName, "name");
					break;
				case "BasicPriority":
					String priorVal  = ((BasicPriority)allow).getName();
					allowableFieldVals.put(fieldName, priorVal);
					projectFieldAttrib.put(fieldName, "name");
					break;
				case "CustomFieldOption":
					String optValues = ((CustomFieldOption)allow).getValue();
					allowableFieldVals.put(fieldName, optValues);
					projectFieldAttrib.put(fieldName, "value");
					break;
			}
		} catch (Exception e) {
			System.out.println("Error in getting the Allowable field values!");
		}
	}
		
	private Long getIssueTypeID() {
		Long issueID=null;
		try {
			Map<String, CimFieldInfo> fieldInfo = null;
			GetCreateIssueMetadataOptions metaOpts = new GetCreateIssueMetadataOptionsBuilder().withProjectKeys(projKey).build();
			Promise<Iterable<CimProject>> cProjs = issueClient.getCreateIssueMetadata(metaOpts);
			Iterable<CimProject> cimProjs = cProjs.claim();
			
			for (CimProject proj : cimProjs) {
				Iterable <CimIssueType> issueTypes = proj.getIssueTypes();
				
	            for(CimIssueType type: issueTypes){
	                if (type.getName().equalsIgnoreCase("bug")) {
	                	issueID = type.getId();
	                	break;
	                }
	            }
			}
		} catch (Exception e) {}
		return issueID;
	}
	
	
	private void setIssueComponents() {
		try {
			String component = jiraFields.get("Component/s");
			List<String> components = new ArrayList<>();
			if (!component.isEmpty()) {
				String[] comp = component.split(",");
				for (int i=0 ; i<comp.length; i++) {
					components.add(comp[i]);
				}
			}
			issueBuild.setComponentsNames(components);
		} catch (Exception e) {
			System.out.println("Error in setting up components");
		}
	}
	
	
	private Map<String, String> getProjectJIRAFields() {
		try {
			Properties jiraProp = new Properties();
			FileInputStream in = new FileInputStream("./resources/JIRAFields.properties");
			jiraProp.load(in);
			Enumeration keys = jiraProp.keys();
			while(keys.hasMoreElements()) {
			  String Key = (String)keys.nextElement();
			  String Val = (String) jiraProp.get(Key);
			  jiraFields.put(Key, Val);
			}
		} catch (Exception e) {
			System.out.println("Error in reading JIRA Project fields property file");
		}
		return jiraFields;
	}
	
	
	public String createJIRA(String summ) throws IOException {
		
		String newIssueID = null;
		
		try {
			
			Set<String> fieldSet = issueFieldsList.keySet();
			for (String field : fieldSet) {
				if (!field.equalsIgnoreCase("Project") || !field.equalsIgnoreCase("Summary") || 
						!field.equalsIgnoreCase("Description") || !field.equalsIgnoreCase("Priority") ||
						!field.equalsIgnoreCase("Assignee") || !field.equalsIgnoreCase("Reporter") || 
						!field.equalsIgnoreCase("Component/s")) {
					String jiraValue = jiraFields.get(field);
					String fieldID = issueFieldsList.get(field);
					String fieldType = projectFieldsType.get(field);
					String attr = projectFieldAttrib.get(field);
					if (jiraFields.containsKey(field) && !jiraValue.isEmpty()) {
						if (fieldType.equalsIgnoreCase("array")) {
							String[] fieldVals = jiraValue.split(",");
							List<ComplexIssueInputFieldValue> fieldList = new ArrayList<ComplexIssueInputFieldValue>();
							Map<String, Object> mapValues = new HashMap<String, Object>();
							for (int i=0; i<fieldVals.length; i++) {
								if (projectCustFieldType.get(field).contains("cascading")) {
									if (fieldVals[i].contains(";")) {
										String[] child = fieldVals[i].split(";");
										mapValues.put("value", child[0].trim());
										mapValues.put("child", ComplexIssueInputFieldValue.with("value",child[1].trim()));
										issueBuild.setFieldValue(fieldID, new ComplexIssueInputFieldValue(mapValues));
									}
								} else {
									mapValues.put("value", fieldVals[i].trim());
									ComplexIssueInputFieldValue fieldValue = new ComplexIssueInputFieldValue(mapValues);
									fieldList.add(fieldValue);
									issueBuild.setFieldValue(fieldID, fieldList);
								}
								
							}
						} else {
							issueBuild.setFieldValue(fieldID, ComplexIssueInputFieldValue.with("value",jiraValue.trim()));
						}
					}
				}
			}
			
			issueBuild.setProjectKey(projKey);
			issueBuild.setSummary(summ);
			issueBuild.setDescription("Test Description");
			issueBuild.setAssigneeName("murugch");
			issueBuild.setReporterName("murugch");
						
			if (issueFieldsList.containsValue("priority"))
				setIssuePriority();
			
			if (issueFieldsList.containsValue("components"))
				setIssueComponents();
			
			IssueInput issueInp = issueBuild.build();
			BasicIssue bIssue = issueClient.createIssue(issueInp).claim();
			
			newIssueID = bIssue.getKey();
			if (!newIssueID.isEmpty()) {
				System.out.println("Issue Created Successfully : " + newIssueID);
			}
			
		} catch (Exception e) {
			System.out.println("Error in creating JIRA defect : " + e.getMessage());
		} 		
		return newIssueID;
	}
	
	
	public String searchJIRA (String summary) {
		String jiraID=null;
		try {
			String jql = "project = " + projKey + " and (status = \"Open/Reopen\" or status = \"Retest\") and issuetype = Bug and summary ~ '" + summary + "' ORDER BY issuekey DESC";
			SearchResult results = searchClient.searchJql(jql).claim();
			if (results.getTotal()>0) {
				for (BasicIssue result : results.getIssues()) {
					jiraID = result.getKey();
					System.out.println("Search returned JIRA issue ID : " + jiraID);
				}
			 } else {
				 System.out.println("No result(s) found with the search criteria");
			 }
		
		} catch (Exception e) {
			System.out.println("Error in searching JIRA");
		}
		return jiraID;
	}
	
	
	public String getIssueStatus(String issueKey) {
		String status = null;
		try {
			List<IssueRestClient.Expandos> expand= new ArrayList<IssueRestClient.Expandos>();
            expand.add(IssueRestClient.Expandos.TRANSITIONS);
            Promise<Issue>  promisedParent = issueClient.getIssue(issueKey,expand);
            issue = promisedParent.claim();
            status = issue.getStatus().getName();
		} catch (Exception e) {
			
		}
		return status;
	}
	
	private int getIssueTransitionID(String projKey, String issueKey, String transition) {
		int transID = 0;
		try {
			List<IssueRestClient.Expandos> expand= new ArrayList<IssueRestClient.Expandos>();
            expand.add(IssueRestClient.Expandos.TRANSITIONS);
            Promise<Issue>  promisedParent = issueClient.getIssue(issueKey,expand);
            issue = promisedParent.claim();
            URI transURI = issue.getTransitionsUri();
            Promise<Iterable<Transition>> ptransitions = issueClient.getTransitions(transURI);
            Iterable<Transition> transitions = ptransitions.claim();
            for(Transition t: transitions){
                String tName = t.getName();
                if (tName.equals(transition)) {
                	transID = t.getId();
                	break;
                }
            }
		} catch (Exception e) {
			
		}
		return transID;
	}
	
	
	public void setIssueTransition (String issueKey, String trans) {
		try {
			int transID = 0;
			transID = getIssueTransitionID(projKey, issueKey, trans);
			Promise<Issue>  promisedParent = issueClient.getIssue(issueKey);
            issue = promisedParent.claim();
            TransitionInput tinput = new TransitionInput(transID, Comment.valueOf("Testing JIRA Close"));
        	issueClient.transition(issue,tinput).claim();
        	System.out.println("Issue Status : " + getIssueStatus(issueKey));
       
		} catch (Exception e) {
			System.out.println("Error : " + e.getMessage());
		} 
	}
	
	
	public void addIssueComment(String issueKey, String comments) {
		try {
			Promise<Issue>  promisedParent = issueClient.getIssue(issueKey);
            issue = promisedParent.claim();
			URI commentsURI = issue.getCommentsUri();
            issueClient.addComment(commentsURI, Comment.valueOf(comments));
            System.out.println("Comments added successfully");
		} catch (Exception e) {
			System.out.println("Error in adding comments");
		}
	}
	
	
	private Map<String,String> parseJSON (JSONObject json) {
		
		Map<String,String> out = new HashMap<String, String>();
		try {
			Iterator<String> keys = json.keys();
		    while(keys.hasNext()){
		        String key = keys.next();
		        String val = null;
		        try{
		             JSONObject value = json.getJSONObject(key);
		             parseJSON (value);
		        }catch(Exception e){
		            val = json.getString(key);
		        }
	
		        if(val != null){
		            out.put(key,val);
		        }
		    }
		} catch (Exception e) {
			System.out.print("Error in parsing the JSON Object : " + json.toString());
		}
	    return out;
	}

	
	public void addIssueAttachment(String issueKey, String filePath) {
		try {
			Issue issue = issueClient.getIssue(issueKey).claim();
			File attachFile = new File(filePath);
			InputStream fileStream = new FileInputStream(attachFile);
			issueClient.addAttachment(issue.getAttachmentsUri(), fileStream, attachFile.getName()).claim();
			System.out.println("Successfully attached file : '" + attachFile.getName() + "'");
		} catch (Exception e) {
			System.out.println("Failed to add attachments!");
		}
	}
	
	
	/**
	 * @date 09/17/2015
	 * @author Chandrasekar Murugesan
	 * @description Method used to encode the secure value
	 * @param 
	 */
	public void encodeValues(String value) {
		byte[] encode=null;
		byte[] decode=null;
		try {
			encode = Base64.encodeBase64(value.getBytes());
			String enValue = new String(encode);
			decode =Base64.decodeBase64(enValue.getBytes());
			String deValue = new String(decode);
			//System.out.println("Original Value : " +deValue);
			System.out.println("Encoded Value : " +enValue);
		} catch (Exception e) {
			//System.out.println("Error in Encoding the value");
		}
	}
	
	
	public String decodeValues(String encodedValue) {
		byte[] decode=null;
		String deValue=null;
		try {
			decode =Base64.decodeBase64(encodedValue.getBytes());
			deValue = new String(decode);
		} catch (Exception e) {
			//System.out.println("Error in Decoding the value");
		}
		return deValue;
	}
}
