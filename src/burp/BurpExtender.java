/***
 * SSRF-King
 * Author: zoid
 * Description:
 * SSRF Plugin for burp that Automates SSRF Detection in all of the Request
 */

package burp;

import java.awt.Checkbox;
import java.awt.Component;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.TextEvent;
import java.awt.event.TextListener;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/***
 * This is the main extension class.
 * @author User
 *
 */
public class BurpExtender implements IBurpExtender, IExtensionStateListener, IScannerCheck  {
    private IBurpCollaboratorClientContext context;
    private PrintWriter stdout;
    public IBurpExtenderCallbacks callback;
	public IExtensionHelpers helpers;
	public String payload;
	private HashSet<String> client_ips;
	public CustomTabUI Ui;

	
	
	
	
    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks)
    {
    	stdout = new PrintWriter(callbacks.getStdout(), true);
    	this.callback=callbacks;
    	helpers=callbacks.getHelpers();
        callbacks.setExtensionName("SSRF-King 1.12");

        stdout.println("Contributor:\n\tBlake (zoid) (twitter.com/z0idsec)\n\t");
        stdout.println("Installation complete.");
        context=callbacks.createBurpCollaboratorClientContext();
        
        payload=context.generatePayload(true);

        callbacks.registerExtensionStateListener(this);
        callbacks.registerScannerCheck(this);
        
        stdout.println("Payload: " + payload + "\n");
        
        client_ips=GetUserIP();

       
        // Create a new User Interface Object
        Ui = new CustomTabUI();
        Ui.SetPayloadUI(payload);
        Ui.CreateUI();
        
        CustomTab tab = new CustomTab("SSRF-King", Ui.GetUI());
        callbacks.addSuiteTab(tab);
    }

	@Override
	public void extensionUnloaded() {
		stdout.println("Finished..");
	}
    

	@Override
	public int consolidateDuplicateIssues(IScanIssue arg0, IScanIssue arg1) {
		return 0;
	}

	@Override
	public List<IScanIssue> doActiveScan(IHttpRequestResponse arg0, IScannerInsertionPoint arg1) {
		List<IScanIssue> issues = new ArrayList<IScanIssue>();
		return issues;
	}
	
	
	/***
	 * Checks to see if any interactions are not coming from us.
	 * @return
	 */
	public HashSet<String> GetUserIP() {
		HashSet<String> client_ips = new HashSet<>();

	        try {
	            String pollPayload = context.generatePayload(true);
	            callback.makeHttpRequest(pollPayload, 80, false, ("GET / HTTP/1.1\r\nHost: " + pollPayload + "\r\n\r\n").getBytes());
	            for (IBurpCollaboratorInteraction interaction: context.fetchCollaboratorInteractionsFor(pollPayload)) {
	                client_ips.add(interaction.getProperty("client_ip"));
	            }
	            stdout.println("Calculated your IPs: "+ client_ips.toString());
	        }
	        catch (NullPointerException e) {
	        	stdout.println("Unable to calculate client IP - collaborator may not be functional");
	        }
	        catch (java.lang.IllegalArgumentException e) {
	        	stdout.println("The Collaborator appears to be misconfigured. Please run a health check via Project Options->Misc. Also, note that Collaborator Everywhere does not support the IP-address mode.");
	        }
	        return client_ips;

	}

	
	/***
	 * Scan passively with burps scanning capabilities.
	 * @param content
	 */
	@Override
	public List<IScanIssue> doPassiveScan(IHttpRequestResponse content) {
	
		List<IScanIssue> issues = new ArrayList<IScanIssue>();
		
		if (callback.isInScope(helpers.analyzeRequest(content).getUrl())) {
			// Run the detection analysis
			this.RunDetectionAnalysis(content, issues);
		}
		
		if (!issues.isEmpty()) {
			return issues;
		}
        return null;

	}
	
	
	/***
	 * Runs various tests against the request to find any interactions
	 * @param content
	 * @param issues
	 */
	public void RunDetectionAnalysis(IHttpRequestResponse content, List<IScanIssue> issues) {
		
		byte[] request = content.getRequest();
		IHttpService service = content.getHttpService();
		IRequestInfo reqInfo = helpers.analyzeRequest(request);
		
		// Test cases for a "GET" request
		if (reqInfo.getMethod().equals("GET")) {
			//RunTestOnParameters("GET", issues, reqInfo,  content, request, service, IParameter.PARAM_URL);
			//RunTestOnXForwardedHost("GET", issues, reqInfo, content, service);
			RunTestOnHostHeader("GET", issues, reqInfo, content, service);
			//RunTestInUserAgent("GET", issues, reqInfo, content, service);
			RunTestInPath("GET", issues, reqInfo, content, service);
			//RunTestInReferer("GET", issues, reqInfo, content, service);
		}
		
		// Test cases for a "POST" request
		if (reqInfo.getMethod().equals("POST")) {
			//RunTestOnParameters("POST", issues, reqInfo, content, request, service, IParameter.PARAM_BODY);
			//RunTestOnXForwardedHost("POST", issues, reqInfo, content, service);
			RunTestOnHostHeader("POST", issues, reqInfo, content, service);
			//RunTestInUserAgent("POST", issues, reqInfo, content, service);
			RunTestInPath("POST", issues, reqInfo, content, service);
			//RunTestInReferer("POST", issues, reqInfo, content, service);
		}
	}
	
	
	/***
	 *  Run SSRF tests on Parameters.
	 * @param method
	 * @param issues
	 * @param reqInfo
	 * @param content
	 * @param request
	 * @param service
	 * @param paramType
	 */
	public void RunTestOnParameters(String method, 
			List<IScanIssue> issues, 
			IRequestInfo reqInfo, 
			IHttpRequestResponse content, 
			byte[] request,
			IHttpService service,
			byte paramType) {
		
	    payload=context.generatePayload(true);
	    Ui.SetPayloadUI(payload);
	
		
		URL url = helpers.analyzeRequest(content).getUrl();
		String path = reqInfo.getHeaders().get(0);
		String host = reqInfo.getHeaders().get(1);
		List<IParameter> params = reqInfo.getParameters();
		
		// Fetch all parameters and inject our Payload.
		for(int i=0; i < params.size(); i++) {
			IParameter param = params.get(i);
			
			// Build the request and update each part of the request with the Payload
			IParameter newParam;
			if (Ui.isHttp) {
		       	newParam = helpers.buildParameter(param.getName(), "http://"+payload, paramType);
			}else {
		       	newParam = helpers.buildParameter(param.getName(), "https://"+payload, paramType);
			}

			if(param.getType() != IParameter.PARAM_COOKIE && !param.getName().contains("_csrf")) {
				request = helpers.updateParameter(request, newParam);
			             	
		
				callback.makeHttpRequest(content.getHttpService(), request);
			    for(IBurpCollaboratorInteraction interaction : context.fetchCollaboratorInteractionsFor(payload)) {
			    	 String client_ip = interaction.getProperty("client_ip");
			        	
			    	 if (client_ips.contains(client_ip)) {
			    		 stdout.println("Open Redirect Found");
			    		 stdout.println("IP: " + client_ip);
				         stdout.println("Host: " + host);
				         stdout.println("Path: " + path);
				         stdout.println("Method: " + method);
				        	
				         String title="Url Redirection";
				         String message="<br>EndPoint:</br><b> " + path + "</b>n";
				         CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "Low", "Certain", "Panic");
				         issues.add(issue);
				        	
				         callback.addScanIssue(issue);
				        	
			    	 }else {
			        	
			        	 stdout.println("Found SSRF");
				         stdout.println("IP: " + client_ip);
				         stdout.println("Host: " + host);
				         stdout.println("Path: " + path);
				         stdout.println("Method: " + method);
				         
				         String title="Parameter Based SSRF";
						 String message="<br>Method: <b>"  + method + "\n</b><br>EndPoint: <b>" + path + "\n</b><br>\nLocation: <b>Parameter</b>\n";
				         CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "High", "Certain", "Panic");
				         issues.add(issue);
				        	
				         callback.addScanIssue(issue);
			    	 }
			    }   
			}
		}
	}
	
	
	/***
	 * Override the X-Forwarded-Host header to test for SSRF
	 * @param method
	 * @param issues
	 * @param reqInfo
	 * @param content
	 * @param service
	 */
	public void RunTestOnXForwardedHost(String method,
			List<IScanIssue> issues, 
			IRequestInfo reqInfo, 
			IHttpRequestResponse content, 
			IHttpService service) {
		
		payload=context.generatePayload(true);
		Ui.SetPayloadUI(payload);
		
		URL url = helpers.analyzeRequest(content).getUrl();
		String path = reqInfo.getHeaders().get(0);
		String host = reqInfo.getHeaders().get(1);
		List<String> headers = reqInfo.getHeaders();
		headers.add("X-Forwarded-Host: " + payload);
		byte[] request = helpers.buildHttpMessage(headers, null);
				             	
			
		callback.makeHttpRequest(content.getHttpService(), request);
		for(IBurpCollaboratorInteraction interaction : context.fetchCollaboratorInteractionsFor(payload)) {
			String client_ip = interaction.getProperty("client_ip");
				        	
			if (client_ips.contains(client_ip)) {
				stdout.println("Open Redirect Found");
			    stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					        	
				String title="Url Redirection";
				String message="<br>EndPoint:<b> " + path + "<br>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "Low", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
					        	
			}else {
				        	
				stdout.println("Found SSRF");
				stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					         
				String title="X-Forwarded-Host Based SSRF";
				String message="<br>Method: <b>"  + method + "\n</b><br>EndPoint: <b>" + path + "\n</b><br>\nLocation: <b>X-Forwarded-Host</b>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "High", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
			}
		}
	}

	
	/***
	 * Run Tests against the Host Header to see if there are any routing issues.
	 * @param method
	 * @param issues
	 * @param reqInfo
	 * @param content
	 * @param service
	 */
	public void RunTestOnHostHeader(String method, 
			List<IScanIssue> issues, 
			IRequestInfo reqInfo, 
			IHttpRequestResponse content, 
			IHttpService service) {
		
		payload=context.generatePayload(true);
		Ui.SetPayloadUI(payload);
		
		URL url = helpers.analyzeRequest(content).getUrl();
		String path = reqInfo.getHeaders().get(0);
		String host = reqInfo.getHeaders().get(1);
		
		List<String> headers = reqInfo.getHeaders();
		headers.set(1, "Host: " + payload);
				             	
		byte[] request = helpers.buildHttpMessage(headers, null);
		callback.makeHttpRequest(content.getHttpService(), request);
		for(IBurpCollaboratorInteraction interaction : context.fetchCollaboratorInteractionsFor(payload)) {
			String client_ip = interaction.getProperty("client_ip");
				        	
			if (client_ips.contains(client_ip)) {
				stdout.println("Open Redirect Found");
			    stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					        	
				String title="Url Redirection";
				String message="<br>EndPoint:<b> " + path + "<br>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "Low", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
					        	
			}else {
				        	
				stdout.println("Found SSRF");
				stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					         
				String title="Host Header Based SSRF";
				String message="<br>Method: <b>"  + method + "\n</b><br>EndPoint: <b>" + path + "\n</b><br>\nLocation: <b>Host Header</b>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "High", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
			}
		}
		
		payload=context.generatePayload(true);
		Ui.SetPayloadUI(payload);
		
		String[] hostValue = host.split(" ");
		List<String> headers2 = reqInfo.getHeaders();
		headers2.set(1, "Host: " + hostValue[1] + "@" + payload);
				             	
		byte[] request2 = helpers.buildHttpMessage(headers2, null);
		callback.makeHttpRequest(content.getHttpService(), request2);
		for(IBurpCollaboratorInteraction interaction : context.fetchCollaboratorInteractionsFor(payload)) {
			String client_ip = interaction.getProperty("client_ip");
				        	
			if (client_ips.contains(client_ip)) {
				stdout.println("Open Redirect Found");
			    stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					        	
				String title="Url Redirection";
				String message="<br>EndPoint:<b> " + path + "<br>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "Low", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
					        	
			}else {
				        	
				stdout.println("Found SSRF");
				stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					         
				String title="Host Header Based SSRF";
				String message="<br>Method: <b>"  + method + "\n</b><br>EndPoint: <b>" + path + "\n</b><br>\nLocation: <b>Host Header</b>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "High", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
			}
		}
	}
	
	
	/***
	 * Run tests against the User-Agent header to see if there is any Blind SSRF shellshock issues.
	 * @param method
	 * @param issues
	 * @param reqInfo
	 * @param content
	 * @param service
	 */
	public void RunTestInUserAgent(String method, 
			List<IScanIssue> issues, 
			IRequestInfo reqInfo, 
			IHttpRequestResponse content, 
			IHttpService service) {
		
		payload=context.generatePayload(true);
		Ui.SetPayloadUI(payload);
		
		URL url = helpers.analyzeRequest(content).getUrl();
		String path = reqInfo.getHeaders().get(0);
		String host = reqInfo.getHeaders().get(1);
		
		boolean foundHeader = false;
		
		List<String> headers = reqInfo.getHeaders();
		for (int i = 0; i < headers.size(); i++) {
			if (headers.get(i).contains("User-Agent")) {
				headers.set(i, "User-Agent: " + payload);
				foundHeader=true;
				break;
			}
		}
		
		if (foundHeader==false) {
			headers.add("User-Agent: " + payload);
		}
				             	
		byte[] request = helpers.buildHttpMessage(headers, null);
		callback.makeHttpRequest(content.getHttpService(), request);
		for(IBurpCollaboratorInteraction interaction : context.fetchCollaboratorInteractionsFor(payload)) {
			String client_ip = interaction.getProperty("client_ip");
				        	
			if (client_ips.contains(client_ip)) {
				stdout.println("Open Redirect Found");
			    stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					        	
				String title="Url Redirection";
				String message="<br>EndPoint:<b> " + path + "<br>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "Low", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
					        	
			}else {
				        	
				stdout.println("Found SSRF");
				stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					         
				String title="User-Agent Based SSRF";
				String message="<br>Method: <b>"  + method + "\n</b><br>EndPoint: <b>" + path + "\n</b><br>\nLocation: <b>User-Agent</b>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "High", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
			}
		}
	}
	
	
	/***
	 * NOTE:
	 * Run SSRF tests in the Referer Header, this is generally blind and worst case.
	 * May not get impact with this issue.
	 * 
	 * @param method
	 * @param issues
	 * @param reqInfo
	 * @param content
	 * @param service
	 */
	public void RunTestInReferer(String method, 
			List<IScanIssue> issues, 
			IRequestInfo reqInfo, 
			IHttpRequestResponse content, 
			IHttpService service) {
		
		
		payload=context.generatePayload(true);
		Ui.SetPayloadUI(payload);
		
		URL url = helpers.analyzeRequest(content).getUrl();
		String path = reqInfo.getHeaders().get(0);
		String host = reqInfo.getHeaders().get(1);
		
		
		boolean foundHeader = false;
		List<String> headers = reqInfo.getHeaders();
		for (int i = 0; i < headers.size(); i++) {
			if (headers.get(i).contains("Referer")) {
				if (Ui.isHttp) {
					headers.set(i, "Referer: " + "http://"+payload);	
				}else {
					headers.set(i, "Referer: " + "https://"+payload);
				}
				foundHeader=true;
				break;
			}
		}
		
		if (foundHeader == false) {
			if (Ui.isHttp) {
				headers.add("Referer: " + "http://"+payload);	
			}else {
				headers.add("Referer: " + "https://"+payload);	
			}
		}
		
		byte[] request = helpers.buildHttpMessage(headers, null);
		
		
		callback.makeHttpRequest(content.getHttpService(), request);
		for(IBurpCollaboratorInteraction interaction : context.fetchCollaboratorInteractionsFor(payload)) {
			String client_ip = interaction.getProperty("client_ip");
				        	
			if (client_ips.contains(client_ip)) {
				stdout.println("Open Redirect Found");
			    stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					        	
				String title="Url Redirection";
				String message="<br>EndPoint:<b> " + path + "<br>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "Low", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
					        	
			}else {
				        	
				stdout.println("Found SSRF");
				stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					         
				String title="Referer Based SSRF";
				String message="<br>Method: <b>"  + method + "\n</b><br>EndPoint: <b>" + path + "\n</b><br>\nLocation: <b>Referer</b>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "High", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
			}
		}
	}
	
	
	/***
	 * Run tests in the path to see if we can get any interactions.
	 * @param method
	 * @param issues
	 * @param reqInfo
	 * @param content
	 * @param service
	 */
	public void RunTestInPath(String method, 
			List<IScanIssue> issues, 
			IRequestInfo reqInfo, 
			IHttpRequestResponse content, 
			IHttpService service) {
		
		payload=context.generatePayload(true);
		Ui.SetPayloadUI(payload);
		
		URL url = helpers.analyzeRequest(content).getUrl();
		String path = reqInfo.getHeaders().get(0);
		String host = reqInfo.getHeaders().get(1);
		
		List<String> headers1 = reqInfo.getHeaders();
		List<String> headers2 = reqInfo.getHeaders();
		List<String> headers01 = reqInfo.getHeaders();
		List<String> headers02 = reqInfo.getHeaders();
	
        // @host/
		String[] pathParts1 = path.split(" ");
		String newPath1 = method + " " + "@"+payload+pathParts1[1] + " HTTP/1.1";
		headers1.set(0, newPath1);
		
		byte[] request1 = helpers.buildHttpMessage(headers1, null);
		callback.makeHttpRequest(content.getHttpService(), request1); 
		
		
        // host:80/
		String[] pathParts01 = path.split(" ");
		String newPath01 = method + " " + payload + ":80" + pathParts01[1] + " HTTP/1.1";
		headers01.set(0, newPath01);
		
		byte[] request01 = helpers.buildHttpMessage(headers01, null);
		callback.makeHttpRequest(content.getHttpService(), request01); 


        // allowed:80@internal/
		String[] pathParts02 = path.split(" ");
		String newPath02 = method + " " + host.split(" ")[1] + ":80" + "@" + payload + pathParts02[1] + " HTTP/1.1";
		headers02.set(0, newPath02);
		
		byte[] request02 = helpers.buildHttpMessage(headers02, null);
		callback.makeHttpRequest(content.getHttpService(), request02); 


        // * full url * 
		String[] pathParts2 = path.split(" ");
		String newPath2;
		if (Ui.isHttp) {
			newPath2 = method + " " + "http://"+payload+pathParts2[1] + " HTTP/1.1";	
		}else {
			newPath2 = method + " " + "https://"+payload+pathParts2[1] + " HTTP/1.1";	
		}
		headers2.set(0, newPath2);
		
		byte[] request = helpers.buildHttpMessage(headers2, null);
		callback.makeHttpRequest(content.getHttpService(), request); 


		for(IBurpCollaboratorInteraction interaction : context.fetchCollaboratorInteractionsFor(payload)) {
			String client_ip = interaction.getProperty("client_ip");
				        	
			if (client_ips.contains(client_ip)) {
				stdout.println("Open Redirect Found");
			    stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					        	
				String title="Url Redirection";
				String message="<br>EndPoint:<b> " + path + "<br>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "Low", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
					        	
			}else {
				        	
				stdout.println("Found SSRF");
				stdout.println("IP: " + client_ip);
				stdout.println("Host: " + host);
				stdout.println("Path: " + path);
				stdout.println("Method: " + method);
					         
				String title="Path Based SSRF";
				String message="<br>Method: <b>"  + method + "\n</b><br>EndPoint: <b>" + path + "\n</b><br>\nLocation: <b>Path</b>\n";
				CustomScanIssue issue=new CustomScanIssue(service, url, new IHttpRequestResponse[]{content} , title, message, "High", "Certain", "Panic");
				issues.add(issue);
					        	
				callback.addScanIssue(issue);
			}
		}
	}
	
	
	
	/***
	 * Scan Issue Class.
	 * @author User
	 *
	 */
	class CustomScanIssue implements IScanIssue {
	    private IHttpService httpService;
	    private URL url;
	    private IHttpRequestResponse[] httpMessages;
	    private String name;
	    private String detail;
	    private String severity;
	    private String confidence;
	    private String remediation;

	    // Constructor
	    CustomScanIssue(
	            IHttpService httpService,
	            URL url,
	            IHttpRequestResponse[] httpMessages,
	            String name,
	            String detail,
	            String severity,
	            String confidence,
	            String remediation) {
	        this.name = name;
	        this.detail = detail;
	        this.severity = severity;
	        this.httpService = httpService;
	        this.url = url;
	        this.httpMessages = httpMessages;
	        this.confidence = confidence;
	        this.remediation = remediation;
	    }

	    @Override
	    public URL getUrl() {
	        return url;
	    }

	    @Override
	    public String getIssueName() {
	        return name;
	    }

	    @Override
	    public int getIssueType() {
	        return 0;
	    }

	    @Override
	    public String getSeverity() {
	        return severity;
	    }

	    @Override
	    public String getConfidence() {
	        return confidence;
	    }

	    @Override
	    public String getIssueBackground() {
	        return null;
	    }

	    @Override
	    public String getRemediationBackground() {
	        return null;
	    }

	    @Override
	    public String getIssueDetail() {
	        return detail;
	    }

	    @Override
	    public String getRemediationDetail() {
	        return remediation;
	    }

	    @Override
	    public IHttpRequestResponse[] getHttpMessages() {
	        return httpMessages;
	    }

	    @Override
	    public IHttpService getHttpService() {
	        return httpService;
	    }

	    public String getHost() {
	        return null;
	    }

	    public int getPort() {
	        return 0;
	    }

	    public String getProtocol() {
	        return null;
	    }
	}
	
	// The Custom Tab UI
	public class CustomTabUI implements TextListener,ItemListener {
		public boolean isHttp = false;
		public Checkbox check;
		public TextField textField;
		public Panel panel;
		public String payload;
		
		
		public void SetPayloadUI (String thispayload) {
			payload= thispayload;
		}
		
		
		/***
		 * Create the User Interface
		 */
		public void CreateUI () {
			panel = new Panel();
		    textField = new TextField();
		    textField.addTextListener(this);
		    textField.setText(payload);
		    Label label = new Label();
		    label.setText("Payload:");
		    Label httpLabel = new Label();
		    httpLabel.setText("IsHttp:");
		    check = new Checkbox();
		    isHttp = check.getState();
		    check.addItemListener(this);
		    panel.add(label);
		    panel.add(textField);
		    panel.add(httpLabel);
		    panel.add(check);
		}
		
		
		/***
		 * Get the User Interface
		 * @return
		 */
		public Component GetUI () {
			return panel;
		}
		
		@Override
		public void textValueChanged(TextEvent e) {
			this.payload = textField.getText();
			
		}
		
		@Override
		public void itemStateChanged(ItemEvent arg0) {
			isHttp = check.getState();
			
		}

	}
	
	// Custom Tab Class
	public class CustomTab implements ITab {

		public Component component;
		public String tabCaption;
		
		// Constructor
		public CustomTab (String _tabCaption, Component _component) {
			this.tabCaption = _tabCaption;
			this.component = _component;
		}
		
		@Override
		public String getTabCaption() {
			this.tabCaption = "SSRF-King";
			return this.tabCaption;
		}

		@Override
		public Component getUiComponent() {
			return this.component;
		}
		
	}
}
