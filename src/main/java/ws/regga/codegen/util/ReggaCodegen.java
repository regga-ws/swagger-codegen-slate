package ws.regga.codegen.util;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenResponse;
import io.swagger.codegen.DefaultCodegen;
import io.swagger.codegen.examples.ExampleGenerator;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.parser.util.ClasspathHelper;
import io.swagger.parser.util.RemoteUrl;

public abstract class ReggaCodegen extends DefaultCodegen implements CodegenConfig {

    private Swagger swagger;
    private JsonNode swaggerJsonNode;
    private Map<String, ReggaSniplet> reggaSniplets = new HashMap<String, ReggaSniplet>();
    private Map<String, ReggaStory> reggaStories = null; // instanciated on first invocation
	
    public ReggaCodegen() {
		super();
		embeddedTemplateDir = "templates"; 
	}

	protected static abstract class CustomLambda implements Mustache.Lambda {
        @Override
        public void execute(Template.Fragment frag, Writer out) throws IOException {
            out.write(getOutput(frag));
        }
        public abstract String getOutput(Template.Fragment frag);
    }
	
    protected static class ReggaSniplet {    	
    	// pojo attributes
    	public String contentType;
    	public String requestPath;
    	public String requestMethod;
    	public String responseCode;
    	// json attributes
    	public String id;
    	public String title;
    	public List<String> tags;
    	public String url;    	
    	public Map<String, String> headers;
    	public Object data;
    	public String requestSnipletId;
    	public String responseSnipletId;
    }
    
    protected enum ReggaSnipletType {
    	REQUEST,
    	RESPONSE
    }
    
    protected static class ReggaStory {
    	public String id;
    	public String title;
    	public List<String> tags;
    	public List<String> snipletSequence;
    }

    protected Swagger getSwagger() {
    	return swagger;
    }
    
    protected JsonNode getSwaggerJsonNode() {
		if (swaggerJsonNode == null) {
			try {
		        String location = getInputSpec().replaceAll("\\\\","/");
				String data;
		        if (location.toLowerCase().startsWith("http")) {
		            data = RemoteUrl.urlToString(location, null);
		        } else {
		            final String fileScheme = "file://";
		            Path path;
		            if (location.toLowerCase().startsWith(fileScheme)) {
		                path = Paths.get(URI.create(location));
		            } else {
		                path = Paths.get(location);
		            }
		            if (Files.exists(path)) {
		                data = FileUtils.readFileToString(path.toFile(), "UTF-8");
		            } else {
		                data = ClasspathHelper.loadFileFromClasspath(location);
		            }
		        }
		        swaggerJsonNode = new ObjectMapper().readTree(data);
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
		return swaggerJsonNode;
    }  
    
	protected Map<String, ReggaSniplet> getReggaSniplets() {
		return reggaSniplets;
    }
    
    protected Map<String, ReggaStory> getReggaStories() {
    	if (reggaStories == null) {
    		reggaStories = new HashMap<String, ReggaStory>();
    		try {
	        	JsonNode swaggerRaw = getSwaggerJsonNode();
	            if (swaggerRaw != null && swaggerRaw.get("x-regga-stories") != null && swaggerRaw.get("x-regga-stories").isArray()) {
	            	Iterator<JsonNode> storiesNode = swaggerRaw.get("x-regga-stories").elements();
	            	while (storiesNode.hasNext()) {
	            		JsonNode storyNode = storiesNode.next();
	    				if (storyNode.get("x-regga-type") == null || !storyNode.get("x-regga-type").asText().equals("story")) continue;
	    				
	    				ReggaStory story = new ReggaStory();
	    				story.id = storyNode.get("id") != null && storyNode.get("id").isTextual() ? sanitizeName(storyNode.get("id").asText()) : "story" + new Random().nextInt(999999);
	    				story.title = storyNode.get("title") != null && storyNode.get("title").isTextual() ? sanitizeName(storyNode.get("title").asText()) : null;
	    				story.tags = null;
	    				story.snipletSequence = null;
	    				
	    				if (storyNode.get("tags") != null && storyNode.get("tags").isArray()) {
	    					story.tags = new ArrayList<String>();
	    		        	Iterator<JsonNode> tags = storyNode.get("tags").elements();
	    		        	while (tags.hasNext()) {
	    		        		JsonNode tag = tags.next();
	    		        		story.tags.add(tag.asText());
	    		        	}
	    				}
	            		
	    				if (storyNode.get("snipletSequence") != null && storyNode.get("snipletSequence").isArray()) {
	    					story.snipletSequence = new ArrayList<String>();
	    		        	Iterator<JsonNode> snipletSequence = storyNode.get("snipletSequence").elements();
	    		        	while (snipletSequence.hasNext()) {
	    		        		JsonNode snipletId = snipletSequence.next();
	    		        		story.snipletSequence.add(snipletId.asText());
	    		        	}
	    				}
	    				
	    				if (reggaStories.get(story.id) != null) throw new Exception("Regga Story ids must be unique");
	    				reggaStories.put(story.id, story);
	            	}
	            }
    		}
    		catch(Exception e) {
    			e.printStackTrace();
    		}
    	}
    	return reggaStories;
    }
    
    @Override
	public void preprocessSwagger(Swagger swagger) {
    	this.swagger = swagger;
    }
    
    private ReggaSniplet parseReggaSniplet(Map<String, ReggaSniplet> reggaSniplets, CodegenOperation operation, String contentType, JsonNode snipletNode, ReggaSnipletType snipletType, boolean trustAsSniplet) throws Exception {
    	
		if (!trustAsSniplet && (snipletNode.get("x-regga-type") == null || !snipletNode.get("x-regga-type").asText().equals("sniplet"))) {
			LOGGER.warn("Example is not of Regga Sniplet type");
			return null;
		}
		
		ReggaSniplet sniplet = new ReggaSniplet();
		
		sniplet.id = snipletNode.get("id") != null && snipletNode.get("id").isTextual() ? snipletNode.get("id").asText() : "sniplet" + new Random().nextInt(999999);
		sniplet.title = snipletNode.get("title") != null && snipletNode.get("title").isTextual() ? snipletNode.get("title").asText() : null;
		sniplet.data = snipletNode.get("data")  != null && snipletNode.get("data").isTextual() ? snipletNode.get("data").textValue() : null;
		
		if (snipletNode.get("tags") != null && snipletNode.get("tags").isArray()) {
			sniplet.tags = new ArrayList<String>();
        	Iterator<JsonNode> tags = snipletNode.get("tags").elements();
        	while (tags.hasNext()) {
        		JsonNode tag = tags.next();
        		sniplet.tags.add(tag.asText());
        	}
		}
		
		Map<String, String> headers = new HashMap<String, String>();									
		JsonNode requestHeadersNode = snipletNode.get("headers");
		if (requestHeadersNode != null) {
			Iterator<String> requestHeadersNodeAttrs = requestHeadersNode.fieldNames();
			while (requestHeadersNodeAttrs.hasNext()) {
				String requestHeadersNodeAttr = requestHeadersNodeAttrs.next();											
				headers.put(requestHeadersNodeAttr, requestHeadersNode.get(requestHeadersNodeAttr).textValue());
			}
		}
		sniplet.headers = headers;
		
		String url = null;
		if (snipletNode.get("uri") != null && (snipletNode.get("uri").asText().startsWith("http") || snipletNode.get("uri").asText().startsWith("ws"))) {
			url = snipletNode.get("uri").toString();
		}
		else {			
			String scheme = swagger.getSchemes() != null && swagger.getSchemes().size() > 0 ? swagger.getSchemes().get(0).toValue() : "http";
			url = scheme + "://" + swagger.getHost() + swagger.getBasePath();
			if (snipletNode.get("uri") == null) url += operation.path;
			else url += snipletNode.get("uri").asText();
		}
		url = url.replace("?", "\n?").replace("&", "\n&");
		sniplet.url = url;
		
		if (snipletNode.get("requestSnipletId") != null && snipletNode.get("requestSnipletId").isTextual()) sniplet.requestSnipletId = snipletNode.get("requestSnipletId").asText();
		else if (snipletNode.get("requestSniplet") != null) {			
			ReggaSniplet childSniplet = parseReggaSniplet(reggaSniplets, operation, contentType, snipletNode.get("requestSniplet"), ReggaSnipletType.REQUEST, true);
			childSniplet.responseSnipletId = sniplet.id;
			sniplet.requestSnipletId = childSniplet.id;
		}
		
		if (snipletNode.get("responseSnipletId") != null && snipletNode.get("responseSnipletId").isTextual()) sniplet.responseSnipletId = snipletNode.get("responseSnipletId").asText();
		// TODO probably remove the following lines as one response can be related to multiple requests
		else if (snipletNode.get("responseSniplet") != null) {			
			ReggaSniplet childSniplet = parseReggaSniplet(reggaSniplets, operation, contentType, snipletNode.get("responseSniplet"), ReggaSnipletType.RESPONSE, true);
			childSniplet.requestSnipletId = sniplet.id;
			sniplet.responseSnipletId = childSniplet.id;
		}
		
		if (reggaSniplets.get(sniplet.id) != null) throw new Exception("Regga Sniplet ids must be unique");
		reggaSniplets.put(sniplet.id, sniplet);

		sniplet.contentType = contentType;
		if (snipletType == ReggaSnipletType.REQUEST) {
			sniplet.requestPath = operation.path;
			sniplet.requestMethod = operation.httpMethod;			
		}
		else if (snipletType == ReggaSnipletType.RESPONSE) {
			sniplet.responseCode = operation.responses != null && operation.responses.size() > 0 ? operation.responses.get(0).code : null;
		}
		
		return sniplet;
    }
    
    @Override
    @SuppressWarnings("unchecked")
	public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
    	
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");        
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
        for (CodegenOperation operation : operationList) {
    		
        	// ensure all operation examples are available (necessary as DELETE examples are for some reason not include during the swagger parsing)
        	if (operation.examples == null && operation.responses.size() > 0 && operation.responses.get(0).examples != null && operation.responses.get(0).examples.size() > 0) {

        		// TODO support other responses than the first/success one ?
        		CodegenResponse successResponse = operation.responses.get(0);
        		
        		Operation operationTmp = swagger.getPaths().get(operation.path).getOperationMap().get(HttpMethod.valueOf(operation.httpMethod));
        		Response responseTmp = operationTmp.getResponses().get(successResponse.code);	
        		operation.examples = new ExampleGenerator(swagger.getDefinitions()).generate(responseTmp.getExamples(), operationTmp.getProduces(), responseTmp.getSchema());
        	}
    		
        	// retrieve regga sniplets
        	if (operation.examples != null) {
    			for (Map<String, String> example : operation.examples) {
    				
        			String contentType = example.get("contentType");
    				String exampleBody = example.get("example");
    				
    				if (contentType.equals("application/json")) {
						try {
							JsonNode json = new ObjectMapper().readTree(exampleBody);
							// example must be an array
							if (json.isArray()) {
								Iterator<JsonNode> i = json.elements();
								while (i.hasNext()) {
									JsonNode snipletNode = i.next();										
									parseReggaSniplet(reggaSniplets, operation, contentType, snipletNode, ReggaSnipletType.RESPONSE, false);
								}
							}
							else {
								LOGGER.warn("Example is not an array of Regga Sniplets");
							}
						} 
						catch (Exception e) {
							e.printStackTrace();
						}
					}
					else if (contentType.equals("application/xml")) {
						// TODO support xml parsing
						LOGGER.warn("XML contentType not supported");
					}
					else {
						LOGGER.warn("Example contentType not supported: " + contentType);
					}		
    			}
        	}
        }
        return objs;
    }
    
    @Override
    public String escapeText(String input) {
        return input;
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        return input;
    }

    @Override
    public String escapeQuotationMark(String input) {
    	return input;
    }
    
}