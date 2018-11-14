Overview
================

Why
----------------

* Helps developers to keep the full history of Avro schemas under source control
* With Avro schemas available from source code, there's no need to:
	* Include a full schema with every serialized message
	* Support an Avro schema registry server


Minor Notes
----------------

The initial implementation of this project focussed on serialization to/from (Avro-defined)
JSON messages. In the future, this may be generalied.


Using this Module
================

There are two steps to configuring your project to use this module.

1. Add the `avro-regisry-in-source-plugin` to your Maven build. This:

	* Exports all Avro schemas (.avsc) from your Avro IDL definition (.avdl) to a temporary directory
	* Validates that the current Avro schemas (.avsc) have already been added to you source directory--thus ensuring that future versions of the software will have this version of the schemas available.
	* Generates Java POJOs from your Avro IDL definition

2. Add the `avro-regisry-in-source` to your Maven dependencies. This:

	* Provides utility methods for reading/writing using the schemas stored in your classpath (JAR)

3. Use the utility methods to access the schemas


Using this Module in Detail
================

Add the `avro-regisry-in-source-plugin` to your Maven build:


	<build>
		<plugins>

			<!-- Build/verify the Avro schemas and build Avro POJOs -->
			<plugin>
				<groupId>com.github.MoebiusSolutions.avro-registry-in-source</groupId>
				<artifactId>avro-registry-in-source-plugin</artifactId>
				<version>1.5</version>
				<configuration>
					<!-- The IDL defining the current schemas -->
					<idlFile>src/main/resources/avro/Main.avdl</idlFile>
					<!-- The directory that contains all current and former schemas
						(committed to revision control) -->
					<schemaSourceDir>src/main/resources/avro-registry</schemaSourceDir>
					<!-- A temporary location where the latest schemas are exported
						(and can be manually copied to the previous location) -->
					<javaTargetDir>target/generated-avro-sources</javaTargetDir>
				</configuration>
				<executions>
					<execution>
						<phase>generate-sources</phase>
						<goals>
							<goal>idl-export-validate-compile</goal>
						</goals>
					</execution>
				</executions>
			</plugin>

			<!--
				Add the generated Java POJOs to the JAR classpath.
				(NOTE: We didn't build this into the main plugin because
				Eclipse would still require this plugin.)
			-->
			<plugin>
				<groupId>org.codehaus.mojo</groupId>
				<artifactId>build-helper-maven-plugin</artifactId>
				<executions>
					<execution>
						<phase>generate-sources</phase>
						<goals>
							<goal>add-source</goal>
						</goals>
						<configuration>
							<sources>
								<source>target/generated-avro-sources</source>
							</sources>
						</configuration>
					</execution>
				</executions>
			</plugin>

		</plugins>
	</build>

Add the `avro-regisry-in-source` to your Maven dependencies:

	<dependencies>
			<dependency>
				<groupId>com.github.MoebiusSolutions.avro-registry-in-source</groupId>
				<artifactId>avro-registry-in-source</artifactId>
				<version>1.5</version>
			</dependency>
	</dependencies>

Use the utility methods to access the schemas:

	AvroJsonLoader loader = new AvroJsonLoader("/avro-registry");

	// Instantiate your POJOs
	House house = House.newBuilder().setRooms(Arrays.asList(
			Room.newBuilder()
			.setBeds(Arrays.asList(
				Bed.newBuilder().setSize(BedSize.KING).build()))
			.build(),
			Room.newBuilder()
			.setBeds(Arrays.asList(
				Bed.newBuilder().setSize(BedSize.QUEEN).build(),
				Bed.newBuilder().setSize(BedSize.TWIN).build()))
			.build()))
	.build();

	// Convert to JSON
	String json = loader.toJson(house, prettyJson);

	// NOTE: At this point, a new version of the IDL definition could be
	// built, but it would contain the old schemas, so the next action would
	// still succeed.

	// Convert back to POJO
	house = loader.fromJson(json, house);


