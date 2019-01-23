package com.github.moebiussolutions.avro_registry_in_source.plugin;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.github.moebiussolutions.avro_registry_in_source.plugin.HistoricalIdlToJavaCompiler;

public class HistoricalIdlToJavaCompilerTest {
	
	private static final File TEST_FILES =
			new File("target/junit-temp", HistoricalIdlToJavaCompilerTest.class.getName()); 

	@Before
	public void setUp() throws Exception {
		if (TEST_FILES.exists()) {
			FileUtils.deleteDirectory(TEST_FILES);
		}
		FileUtils.forceMkdir(TEST_FILES);
	}

	/**
	 * Verifies that, when no exceptions occur, {@link HistoricalIdlToJavaCompiler#main(String...)}:
	 * 
	 * <ul>
	 * <li>Exports schema files to the temp directory</li>
	 * <li>Generates java bindings</li>
	 * </ul>
	 */
	@Test
	public void test_Success() throws Exception {
		// Setup
		HistoricalIdlToJavaCompiler mojo = new HistoricalIdlToJavaCompiler();
		mojo.idlFile = new File("src/test/resources/"+HistoricalIdlToJavaCompilerTest.class.getSimpleName()+"/Main.avdl");
		mojo.schemaTempDir = new File(TEST_FILES, "schema-temp");
		mojo.schemaSourceDir = new File("src/test/resources/"+HistoricalIdlToJavaCompilerTest.class.getSimpleName());
		mojo.javaTargetDir = new File(TEST_FILES, "java");

		// Execute
		mojo.execute();

		// Verify
		// ... Schema files generated
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/Bed_1378068713606829616.avsc").isFile());
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/BedSize_5741753547892987029.avsc").isFile());
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/House_-7599226751060149848.avsc").isFile());
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/Room_-5559107264162428975.avsc").isFile());
		// ... Java files generated
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/Bed.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/BedSize.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/House.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/MockProtocol.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/Room.java").isFile());
	}

	/**
	 * Verifies that {@link HistoricalIdlToJavaCompiler#main(String...)} fails when
	 * schema files are missing from the source directory.
	 */
	@Test
	public void test_MissingSchemas() throws Exception {
		// Setup
		HistoricalIdlToJavaCompiler mojo = new HistoricalIdlToJavaCompiler();
		mojo.idlFile = new File("src/test/resources/"+HistoricalIdlToJavaCompilerTest.class.getSimpleName()+"/Main.avdl");
		mojo.schemaTempDir = new File(TEST_FILES, "schema-temp");
		mojo.schemaSourceDir = new File(TEST_FILES, "schema-source");
		FileUtils.forceMkdir(mojo.schemaSourceDir);
		mojo.javaTargetDir = new File(TEST_FILES, "java");

		// Execute/verify
		try {
			mojo.execute();
			Assert.fail("Expected exception");
		} catch (HistoricalIdlToJavaCompiler.InvalidSchemaFiles e) {
			assertEquals("Missing schema file "
					+ "[target/junit-temp/com.github.moebiussolutions.avro_registry_in_source.plugin.HistoricalIdlToJavaCompilerTest/schema-source/com_example_package/BedSize_5741753547892987029.avsc], "
					+ "which should have been extracted from IDL [src/test/resources/HistoricalIdlToJavaCompilerTest/Main.avdl]", e.getMessage());
		}
	}

	/**
	 * Verifies that {@link HistoricalIdlToJavaCompiler#main(String...)} exports all
	 * schema files (to "schema temp") that are required to be in "schema source".
	 * (Under normal conditions, both paths would not be pointing to the same
	 * location, but by doing so, we ensure that the output is compatible as an
	 * input.)
	 */
	@Test
	public void test_Success_ConsumingSchemaOutput() throws Exception {
		// Setup
		HistoricalIdlToJavaCompiler mojo = new HistoricalIdlToJavaCompiler();
		mojo.idlFile = new File("src/test/resources/"+HistoricalIdlToJavaCompilerTest.class.getSimpleName()+"/Main.avdl");
		mojo.schemaTempDir = new File(TEST_FILES, "schema-temp");
		// ... With schema "temp" and "source" directories identical
		mojo.schemaSourceDir = mojo.schemaTempDir;
		mojo.javaTargetDir = new File(TEST_FILES, "java");

		// Execute
		// ... With schema "temp" and "source" directories identical
		mojo.execute();

		// Verify
		// ... Schema files generated
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/Bed_1378068713606829616.avsc").isFile());
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/BedSize_5741753547892987029.avsc").isFile());
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/House_-7599226751060149848.avsc").isFile());
		assertTrue(new File(mojo.schemaTempDir, "com_example_package/Room_-5559107264162428975.avsc").isFile());
		// ... Java files generated
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/Bed.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/BedSize.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/House.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/MockProtocol.java").isFile());
		assertTrue(new File(mojo.javaTargetDir, "com/example/package/Room.java").isFile());
	}
}
