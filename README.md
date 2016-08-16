# swagger-codegen-slate

## Introduction

Swagger Codegen generator to generate Slate API documentation for a Swagger spec. 

Based on the contents of the Swagger spec (tags, operations, resources), the generator creates an <i>index.html.md</i> file which should be put in the <i>slate/source directory</i> before running the <i>bundle exec middleman build</i> command.

More information about Swagger Codegen and Slate at https://github.com/swagger-api/swagger-codegen and https://github.com/lord/slate.

## Screenshot

<img src="https://docs.google.com/a/estela.fr/uc?authuser=0&id=0B-Bhjw6plRdsMU1LbEhuZTh5dG8&export=download"/>

## Minimal configuration

	<plugin>
		<groupId>io.swagger</groupId>
		<artifactId>swagger-codegen-maven-plugin</artifactId>
		<version>2.2.2-SNAPSHOT</version>
		<executions>
			<execution>
				<phase>install</phase>
				<goals>
					<goal>generate</goal>
				</goals>
				<configuration>
					<language>ws.regga.codegenslate.SlateCodegen</language>
					<inputSpec>http://petstore.swagger.io/v2/swagger.json</inputSpec>		
				</configuration>
			</execution>
		</executions>
		<dependencies>
			<dependency>
				<groupId>ws.regga</groupId>
				<artifactId>swagger-codegen-slate</artifactId>
				<version>1.0.0</version>
			</dependency>
		</dependencies>
	</plugin>

## Configuration with custom template

	<plugin>
		<groupId>io.swagger</groupId>
		<artifactId>swagger-codegen-maven-plugin</artifactId>
		<version>2.2.2-SNAPSHOT</version>
		<executions>
			<execution>
				<phase>install</phase>
				<goals>
					<goal>generate</goal>
				</goals>
				<configuration>
					<language>ws.regga.codegenslate.SlateCodegen</language>
					<inputSpec>http://petstore.swagger.io/v2/swagger.json</inputSpec>
					<!-- must contain index.html.md.mustache -->
					<templateDirectory>custom-template-dir</templateDirectory>
				</configuration>
			</execution>
		</executions>
		<dependencies>
			<dependency>
				<groupId>ws.regga</groupId>
				<artifactId>swagger-codegen-slate</artifactId>
				<version>1.0.0</version>
			</dependency>
		</dependencies>
	</plugin>
	
## Configuration to keep ordering of tags and operations from the Swagger spec

	<plugin>
		<groupId>io.swagger</groupId>
		<artifactId>swagger-codegen-maven-plugin</artifactId>
		<version>2.2.2-SNAPSHOT</version>
		<executions>
			<execution>
				<phase>install</phase>
				<goals>
					<goal>generate</goal>
				</goals>
				<configuration>
					<language>ws.regga.codegenslate.SlateCodegen</language>
					<inputSpec>http://petstore.swagger.io/v2/swagger.json</inputSpec>
					<configOptions>
					  <keepOriginalOrder>true</keepOriginalOrder>
					</configOptions>    
				</configuration>
			</execution>
		</executions>
		<dependencies>
			<dependency>
				<groupId>ws.regga</groupId>
				<artifactId>swagger-codegen-slate</artifactId>
				<version>1.0.0</version>
			</dependency>
		</dependencies>
	</plugin>
