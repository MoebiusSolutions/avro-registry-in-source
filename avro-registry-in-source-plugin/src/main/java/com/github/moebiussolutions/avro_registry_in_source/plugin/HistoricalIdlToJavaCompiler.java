package com.github.moebiussolutions.avro_registry_in_source.plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.avro.Protocol;
import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.compiler.idl.Idl;
import org.apache.avro.compiler.idl.ParseException;
import org.apache.avro.compiler.specific.SpecificCompiler;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@Mojo(name = "idl-export-validate-compile", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class HistoricalIdlToJavaCompiler extends AbstractMojo {

	/**
	 * The root file of the IDL. This may import other files.
	 */
	@Parameter (required = true)
	File idlFile;

	/**
	 * A directory where Avro Schema files (.avsc) should be exported to from the
	 * current Avro IDL (.avdl) file. This directory is generally outside of the files
	 * collected by the build, with the expectation that a human will copy these
	 * files to a source directory and commit them prior to publishing a release.
	 */
	@Parameter (defaultValue = "target/generated-avro-schemas")
	File schemaTempDir;

	/**
	 * A directory where Avro Schema files (.avsc) should exist for the current and
	 * historic states of the Avro IDL (.avdl). This directory should be committed
	 * to source. This is the directory where developers should be manually copying
	 * the contents to (from {@link #schemaTempDir}).
	 */
	@Parameter (defaultValue = "src/main/resources/avro-schemas")
	File schemaSourceDir;

	/**
	 * The root directory where all generated Java (.java) files should be written.
	 * These files should be included by the build, but need not be committed to
	 * source.
	 */
	@Parameter (defaultValue = "target/generated-avro-sources")
	File javaTargetDir;

	public void execute() throws MojoExecutionException {
		if (!this.idlFile.isFile()) {
			throw new RuntimeException("IDL file ["+this.idlFile+"] is not accessible");
		}
		ensureDirectory(this.schemaTempDir, "Temp schema");
		ensureDirectory(this.schemaSourceDir, "Source schema");
		ensureDirectory(this.javaTargetDir, "Java out");
		
		// Execute
		exportSchemas(this.idlFile, this.schemaTempDir);
		try {
			validateIdlSchemaFiles(this.idlFile, this.schemaSourceDir);
		} catch (InvalidSchemaFiles e) {
			System.err.println("");
			System.err.println("ERROR: Missing/invalid Avro schema files in ["+this.schemaSourceDir+"]. ");
			System.err.println("  This is likely resolved by copy the contents of ["+this.schemaTempDir+"] over.");
			System.err.println("");
			throw e;
		}
		generateJava(this.idlFile, this.javaTargetDir);
	}

	@SuppressWarnings("serial")
	static final class InvalidSchemaFiles extends RuntimeException {
		public InvalidSchemaFiles(String message) {
			super(message);
		}
		public InvalidSchemaFiles(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/**
	 * Exports Avro schemas (.avsc) from an Avro IDL (.avdl), with each schema
	 * filename including the schema fingerprint.
	 * 
	 * @param idlFile
	 *            The root file of the IDL. This may import other files.
	 * @param outputDir
	 *            The directory where the schemas should be written. This directory
	 *            will be created as necessary. The output filenames will contain the
	 *            fingerprint of the schema.
	 */
	// Package protected
	static void exportSchemas(File idlFile, File outputDir) {
		try (Idl idl = new Idl(idlFile)) {
			Protocol p = idl.CompilationUnit();
			p.getTypes().iterator().forEachRemaining((s) -> {
				File outFile = getFingerprintedSchemaFile(outputDir, s);
				try {
					FileUtils.write(outFile, s.toString(true), StandardCharsets.UTF_8);
				} catch (IOException e) {
					throw new RuntimeException("Failed to write schema to ["+outFile+"]");
				}
			});
		} catch (ParseException | IOException e) {
			throw new RuntimeException("Failed to process IDL ["+idlFile+"]", e);
		}
	}

	/**
	 * Verifies that all of the Avro schemas (.avsc) contained by an Avro IDL
	 * (.avdl), exist as on disk, with fingerprinted filenames. Throws an exception
	 * if this is not true.
	 * 
	 * @param idlFile
	 *            The root file of the IDL. This may import other files.
	 * @param schemaDir
	 *            The directory where the schemas should exist.
	 * 
	 * @throws InvalidSchemaFiles
	 *             When any of the schemas embedded within the IDL file are missing
	 *             or invalid.
	 */
	// Package protected
	static void validateIdlSchemaFiles(File idlFile, File schemaDir) throws InvalidSchemaFiles {
		try (Idl idl = new Idl(idlFile)) {
			Protocol p = idl.CompilationUnit();
			p.getTypes().iterator().forEachRemaining((s) -> {

				// Verify fingerprinted schema file exists
				long fingerprint = SchemaNormalization.parsingFingerprint64(s);
				File schemaFile = getFingerprintedSchemaFile(schemaDir, s, fingerprint);
				if (!schemaFile.isFile()) {
					throw new InvalidSchemaFiles(
							"Missing schema file ["+schemaFile+"], which should have been extracted from IDL ["+idlFile+"]");
				}

				// Verify contents of fingerprinted schema are valid
				Schema parsedSchema;
				try {
					parsedSchema = new Schema.Parser().parse(schemaFile);
				} catch (IOException e) {
					throw new InvalidSchemaFiles(
							"Failed to parse schema file ["+schemaFile+"]", e);
				}
				long parsdFingerprint = SchemaNormalization.parsingFingerprint64(parsedSchema);
				if (fingerprint != parsdFingerprint) {
					throw new InvalidSchemaFiles(
							"Fingerprint of ["+schemaFile+"] is ["+parsdFingerprint+"] instead of ["+fingerprint+"]");
				}
			});
		} catch (ParseException | IOException e) {
			throw new RuntimeException("Failed to process IDL ["+idlFile+"]", e);
		}
	}

	/**
	 * Generates Java source files from an Avro IDL (.avdl).
	 */
	private static void generateJava(File idlFile, File javaOutDir) {
		try (Idl idl = new Idl(idlFile)) {
			Protocol p = idl.CompilationUnit();
			SpecificCompiler compiler = new SpecificCompiler(p);
			compiler.setOutputCharacterEncoding(StandardCharsets.UTF_8.toString());
			try {
				compiler.compileToDestination(idlFile, javaOutDir);
			} catch (IOException e) {
				throw new RuntimeException("Failed to compile ["+idlFile+"] to ["+javaOutDir+"]", e);
			}
		} catch (ParseException | IOException e) {
			throw new RuntimeException("Failed to process IDL ["+idlFile+"]", e);
		}
	}

	private static void ensureDirectory(File dir, String description) {
		dir.mkdirs();
		if (!dir.isDirectory()) {
			throw new RuntimeException(String.format("%s directory [%s] is not accessible", description, dir));
		}
	}

	private static File getFingerprintedSchemaFile(File schemaDir, Schema s) {
		return getFingerprintedSchemaFile(schemaDir, s, SchemaNormalization.parsingFingerprint64(s));
	}

	private static File getFingerprintedSchemaFile(File schemaDir, Schema s, long fingerprint) {
		return new File(schemaDir, s.getName()+"_"+fingerprint+".avsc");
	}
}
