package ws.regga.codegen.slate;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.StringTokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;

import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenModel;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenProperty;
import io.swagger.codegen.CodegenResponse;
import io.swagger.codegen.CodegenSecurity;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.SupportingFile;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import ws.regga.codegen.util.ReggaCodegen;

public class SlateCodegen extends ReggaCodegen {

    public SlateCodegen() {    	
        super();        
        embeddedTemplateDir = "templates";        
        supportingFiles.add(new SupportingFile("index.html.md.mustache", "", "index.html.md"));        
        cliOptions.add(new CliOption("exampleLanguages", "Languages for which examples should be generated"));
        cliOptions.add(new CliOption("keepOriginalOrder", "Preserve original order of tags and operations"));
    }
    
	private String prettify(Object uglyData) throws Exception {
		// TODO manage object and xml prettification as well
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		Object json = mapper.readValue(uglyData.toString(), Object.class);
		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
	}
    
	private boolean keepOriginalOrder() {
		return additionalProperties.get("keepOriginalOrder") != null 
			&& Boolean.valueOf(additionalProperties.get("keepOriginalOrder").toString());
	}
	
	private List<String> exampleLanguages() {
		List<String> languages = new ArrayList<String>();
		if (additionalProperties.get("exampleLanguages") != null) {
			StringTokenizer st = new StringTokenizer(additionalProperties.get("exampleLanguages").toString(), ",");
			while (st.hasMoreTokens()) {
				languages.add(st.nextToken());
			}
		}
		return languages;
	}
    
    @Override
    public void preprocessSwagger(Swagger swagger) {
    	super.preprocessSwagger(swagger);
		
		try {			
			// keep tags and operations in the original order
			// overrides original operationId and tags
			if (keepOriginalOrder()) {
				JsonNode swaggerRaw = getSwaggerJsonNode();
				Iterator<JsonNode> h = swaggerRaw.get("tags").elements();
				Map<String, String> orderedTags = new HashMap<String, String>();
				String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"; // supports up to 26 tags
				int count = 0;
				while (h.hasNext()) {
					JsonNode node = h.next();
					String tag = node.get("name").textValue();
					// tag names should only contain letters
					// FIXME for now only 26 tags (alphabet size) are supported
					String newTagName = "Custom" + (count <= 26 ? alphabet.charAt(count) : "Z") + tag;
					orderedTags.put(tag, newTagName);
					count++;
				}
				for (Tag tag : swagger.getTags()) {
					tag.setName(orderedTags.get(tag.getName()));
				}
				Iterator<String> i = swaggerRaw.get("paths").fieldNames();
		    	count = 0;
				while (i.hasNext()) {
					count++;
					String pathKey = i.next();
		    		Iterator<String> j = swaggerRaw.get("paths").get(pathKey).fieldNames();
					while (j.hasNext()) {
						count++;
						String method = j.next();
			    		Operation operation = swagger.getPath(pathKey).getOperationMap().get(HttpMethod.valueOf(method.toUpperCase()));
			    		operation.setOperationId("custom" + String.format("%05d", count) + operation.getOperationId());
			    		ArrayList<String> tags = new ArrayList<String>();
			    		for (String oldTag : operation.getTags()) {
			    			tags.add(orderedTags.get(oldTag));
			    		}
			    		operation.setTags(tags);
					}
				}
			}
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
    }

   	@Override
    public void processOpts() {
        super.processOpts();
        
        additionalProperties.put("fnLowerCase", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
	            StringWriter tempWriter = new StringWriter();
	            frag.execute(tempWriter);				
				return tempWriter.toString().toLowerCase();
			}
		});
        
        additionalProperties.put("fnParseExamples", new CustomLambda() {			
        	@Override
			public String getOutput(Template.Fragment frag) {
				
        		StringBuilder builder = new StringBuilder();
				CodegenOperation operation = (CodegenOperation) frag.context();	
				String requestPath = operation.path;
				String requestMethod = operation.httpMethod;
				
				// TODO support more than the first response
				List<Map<String,Object>> successResponseExamples = 
					operation.responses != null && operation.responses.size() > 0 ? operation.responses.get(0).examples : null;	
				
				if (successResponseExamples == null) return builder.toString();
					
				for (Map<String,Object> example : successResponseExamples) {
					String contentType = example.get("contentType").toString();
					
					for (String snipletId : getReggaSniplets().keySet()) {
						ReggaSniplet requestSniplet = getReggaSniplets().get(snipletId);
						if (requestPath.equals(requestSniplet.requestPath) 
							&& requestMethod.equals(requestSniplet.requestMethod) 
							&& contentType.equals(requestSniplet.contentType)) {
							
							try {							
								// found request sniplet, now retrieve response sniplet
								ReggaSniplet responseSniplet = getReggaSniplets().get(requestSniplet.responseSnipletId);
								
								String title = requestSniplet.title != null ? requestSniplet.title : responseSniplet.title;	
								String url = requestSniplet.url != null ? requestSniplet.url : responseSniplet.url;		
								
								builder.append("> " + title + "\n"); 
								
								List<String> languages = exampleLanguages();
								for (String language : languages) {
									
									builder.append("\n"); 
									builder.append("```" + language + "\n"); 
									if (language.equals("shell")) { // TODO manage other languages than shell
										builder.append("curl -X " + requestMethod);
										builder.append("\n-H 'Content-Type: " + contentType + "'");
										builder.append("\n-H 'Accept: " + contentType + "'");
										for (String requestHeaderKey : requestSniplet.headers.keySet()) {
											String requestHeaderValue = requestSniplet.headers.get(requestHeaderKey);
											builder.append("\n-H '" + requestHeaderKey + ": " + requestHeaderValue + "'");
										}
										if (requestSniplet.data != null) builder.append("\n-d '" + prettify(requestSniplet.data) + "' ");
									}
									builder.append("\n" + url + "\n");										
									builder.append("```\n"); 									
								}									
								
								if (responseSniplet != null && responseSniplet.data != null) {	
									builder.append("\n"); 
									builder.append("```json\n");  // TODO manage other languages than json
									builder.append(prettify(responseSniplet.data) + "\n"); 
									builder.append("```\n");	
								} 
								builder.append("\n\n"); 
							}
							catch(Exception e) {
								e.printStackTrace();
							}
						}						
					}
				}
				
				return builder.toString();
			}
		});
    }

    @Override
    @SuppressWarnings("unchecked")
	public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
    	objs = super.postProcessOperations(objs);

        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
        for (CodegenOperation operation : operationList) {
			Operation operationTmp = getSwagger().getPaths().get(operation.path).getOperationMap().get(HttpMethod.valueOf(operation.httpMethod));
        	if (keepOriginalOrder()) {
            	// set back original tag
            	operation.baseName = toApiName(operation.baseName);
            	// set back original id
            	operation.operationId = operation.operationId.substring("custom00000".length());
        	}
        	if (operation.responses != null) {
	        	for (CodegenResponse response : operation.responses) {
	        		// override jsonSchema with ref name
	        		if (response.schema != null) {
	        			if (response.schema instanceof ArrayProperty) {
	        				ArrayProperty array = (ArrayProperty) response.schema;
	        				if (array.getItems() instanceof RefProperty) {
	            				RefProperty ref = (RefProperty) array.getItems();
	            				response.jsonSchema = ref.getSimpleRef();            					
	        				}
	        				else response.jsonSchema = array.getItems().getType(); // FIXME better support of other responses
	        				// set array flag
	        				response.isListContainer = true;
	        			}
	        			else if (response.schema instanceof RefProperty) {
	        				RefProperty ref = (RefProperty) response.schema;
	        				response.jsonSchema = ref.getSimpleRef();
	        			}
	        			else response.jsonSchema = ((Property) response.schema).getType(); // FIXME better support of other responses
	        		}
	        	} 
        	}
        	// make scope usable for securities other than oauth
        	if (operation.authMethods != null) {
	        	for (CodegenSecurity security : operation.authMethods) {	        		
	        		if (security.scopes == null || security.scopes.size() == 0) {
	        			
	        			// check that the original authMethod does not have scopes
	        			for (Map<String, List<String>> securityTmp : operationTmp.getSecurity()) {	        				
	        				List<String> scopesTmp = securityTmp.get(security.name);
	        				
	        				// if there are scopes, add it to the authMethod
	        				if (scopesTmp != null && scopesTmp.size() > 0) {	        					
	    	        			List<Map<String, Object>> scopes = new ArrayList<Map<String, Object>>();
	    	        			security.scopes = scopes;
	    	        			for (String scopeTmp : scopesTmp) {
		    	        			Map<String, Object> scope = new HashMap<String, Object>();
		    	        			scope.put("scope", scopeTmp);
		    	        			scope.put("description", ""); // FIXME		    	        			
		    	        			scopes.add(scope);	    	        				
	    	        			}
	        				}
	        			}
	          		}
	        	}        		
        	}
        }
    	
        for (Tag tag : getSwagger().getTags()) {
        	if (toApiName(tag.getName()).equals(operations.get("classname"))) {
        		objs.put("package", tag.getDescription());
        		break;
        	}
        }
    	
        return objs;
    }
    
    @Override
    @SuppressWarnings("unchecked")
	public Map<String, Object> postProcessAllModels(final Map<String, Object> objs) {
    
    	final Map<String, Object> processed =  super.postProcessAllModels(objs);
        // fix isPrimitive flag
        for (Entry<String, Object> entry : objs.entrySet()) {
            Map<String, Object> inner = (Map<String, Object>) entry.getValue();
            List<Map<String, Object>> models = (List<Map<String, Object>>) inner.get("models");
            for (Map<String, Object> mo : models) {
                CodegenModel cm = (CodegenModel) mo.get("model");
            	for (CodegenProperty property : cm.allVars) {
            		property.isPrimitiveType = (typeMapping.get(property.complexType) != null);
            	}
            }
        }        
        return processed;
    }
    
    @Override
    public String toApiName(String name) {   	
    	name = keepOriginalOrder() ? name.substring("CustomZ".length()) : name;
        return name;
    }

    @Override
    public String getTypeDeclaration(Property p) {
    	String datatype =  p.getType();
    	if (p.getFormat() != null) datatype += " (" + p.getFormat() + ")";
    	return datatype;
    }
    
    @Override
    public Compiler processCompiler(Compiler compiler) {
    	return compiler.escapeHTML(false);
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.DOCUMENTATION;
    }

    @Override
    public String getName() {
        return "slate";
    }

    @Override
    public String getHelp() {
        return "Generates a Slate API documentation.";
    }
}