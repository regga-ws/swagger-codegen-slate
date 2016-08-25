package ws.regga.codegenslate;

import java.io.IOException;
import java.io.StringWriter;
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
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Mustache.Compiler;
import com.samskivert.mustache.Template;

import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.CodegenModel;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenProperty;
import io.swagger.codegen.CodegenResponse;
import io.swagger.codegen.CodegenSecurity;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.DefaultCodegen;
import io.swagger.codegen.SupportingFile;
import io.swagger.codegen.examples.ExampleGenerator;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.parser.util.ClasspathHelper;
import io.swagger.parser.util.RemoteUrl;

public class SlateCodegen extends DefaultCodegen implements CodegenConfig {
    
    private Swagger swagger;
    
    private static abstract class CustomLambda implements Mustache.Lambda {
        @Override
        public void execute(Template.Fragment frag, Writer out) throws IOException {
            out.write(getOutput(frag));
        }
        public abstract String getOutput(Template.Fragment frag);
    }
    
	private String toPrettyJson(String uglyJson) throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		Object json = mapper.readValue(uglyJson, Object.class);
		return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
	}
	
	private String toPrettyCUrlWithJson(String uglyCUrl) throws Exception {
		Pattern p = Pattern.compile("(\\-[a-zA-Z] ((\'[^\']+\')|(\"[^\"]+\")))");
		Matcher m = p.matcher(uglyCUrl);
		List<String> parameters = new ArrayList<String>();
		while (m.find()) {
			parameters.add(m.group(1));
		}
		if (parameters.size() == 0) return uglyCUrl;
		else {
			StringBuilder sb = new StringBuilder();
			sb.append(uglyCUrl.substring(0, uglyCUrl.indexOf(parameters.get(0))));
			for (String parameter : parameters) {
				if (!parameter.startsWith("-d")) sb.append("\n" + parameter + " ");
				else {
					String json = parameter.substring(parameter.indexOf("{"), parameter.lastIndexOf("}")+1);
					String prettyJson = toPrettyJson(json);
					sb.append("\n" + parameter.replace(json, prettyJson) + " ");
				}
			}
			String urlPart = uglyCUrl.substring(uglyCUrl.lastIndexOf(" ")+1);
			urlPart = urlPart.replace("?", "\n?").replace("&", "\n&");
			sb.append("\n" + urlPart);
			return sb.toString();
		}
	}	
    
	private boolean keepOriginalOrder() {
		return additionalProperties.get("keepOriginalOrder") != null 
			&& Boolean.valueOf(additionalProperties.get("keepOriginalOrder").toString());
	}

    public SlateCodegen() {
    	
        super();
        
        embeddedTemplateDir = "templates";        
        supportingFiles.add(new SupportingFile("index.html.md.mustache", "", "index.html.md"));        
        cliOptions.add(new CliOption("keepOriginalOrder", "Preserve original order of tags and operations"));
        
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
			@SuppressWarnings("unchecked")
			public String getOutput(Template.Fragment frag) {
				Map<String, String> examples = (Map<String, String>) frag.context();
				String contentType = examples.get("contentType");
				String example = examples.get("example");	
				StringBuilder builder = new StringBuilder();
				if (example != null && contentType != null) {				
					if (contentType.equals("application/json")) {
						try {
							JsonNode json = new ObjectMapper().readTree(example);
							// support specific x-code-examples syntax
							if (json.get("x-code-examples") != null) {
								Iterator<JsonNode> i = json.get("x-code-examples").elements();
								while (i.hasNext()) {
									JsonNode command = i.next();
									String title = command.get("title").textValue();
									String language = command.get("language").textValue();
									String input = command.get("input").toString().replace("\\\"", "\"");
									input = input.substring(1, input.length()-1);
									builder.append("> " + title + "\n"); 
									builder.append("\n"); 
									builder.append("```" + language + "\n"); 
									builder.append((language.equals("shell") ? toPrettyCUrlWithJson(input) : input) + "\n"); 
									builder.append("```\n"); 
									if (command.get("output") != null) {	
										String output = command.get("output").toString().replace("\\\"", "\"");	
										output = output.substring(1, output.length()-1);
										builder.append("\n"); 
										builder.append("```json\n"); 
										builder.append(toPrettyJson(output) + "\n"); 
										builder.append("```");	
									}
								}
							}
							else {
								builder.append("> JSON response example\n"); 
								builder.append("\n"); 
								builder.append("```json\n"); 
								builder.append(toPrettyJson(json.toString()) + "\n"); 
								builder.append("```\n");
							}
						} 
						catch (Exception e) {
							e.printStackTrace();
						}
					}
					else if (contentType.equals("application/xml")) {
						builder.append("> XML response example\n"); 
						builder.append("\n"); 
						builder.append("```xml\n"); 
						builder.append(example + "\n"); 
						builder.append("```\n");
					}
					else {
						// FIXME only json and xml are supported
						System.out.println("Example contentType not supported: " + contentType);
					}					
				}
				return builder.toString();
			}
		});
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

    @Override
    public void preprocessSwagger(Swagger swagger) {
    	
		this.swagger = swagger;
		
		try {			
			// keep tags and operations in the original order
			// overrides original operationId and tags
			if (keepOriginalOrder()) {
				String data;
	            String location = getInputSpec().replaceAll("\\\\","/");
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
				JsonNode swaggerRaw = new ObjectMapper().readTree(data);
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
    @SuppressWarnings("unchecked")
	public Map<String, Object> postProcessOperations(Map<String, Object> objs) {

        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");
        
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
        for (CodegenOperation operation : operationList) {
			Operation operationTmp = swagger.getPaths().get(operation.path).getOperationMap().get(HttpMethod.valueOf(operation.httpMethod));
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
        	// ensure all operation examples are displayed: display example of first response
        	if (operation.examples == null && operation.responses.size() > 0 && operation.responses.get(0).examples != null && operation.responses.get(0).examples.size() > 0) {
        		Response responseTmp = operationTmp.getResponses().get(operation.responses.get(0).code);	
        		operation.examples = new ExampleGenerator(swagger.getDefinitions()).generate(responseTmp.getExamples(), operationTmp.getProduces(), responseTmp.getSchema());
        	}
        }
    	
        for (Tag tag : swagger.getTags()) {
        	if (toApiName(tag.getName()).equals(operations.get("classname"))) {
        		objs.put("package", tag.getDescription());
        		break;
        	}
        }
    	
        return objs;
    }
    
    @Override
    public String toApiName(String name) {   
    	
    	name = keepOriginalOrder() ? name.substring("CustomZ".length()) : name;
        return name;
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
}