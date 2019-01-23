package com.github.moebiussolutions.avro_registry_in_source.common;

public class CommonLib {

	/**
	 * This converts Avro type names and namespaces to filesystem-safe strings. Note
	 * that all slashes and periods are sanitized out to avoid walking of the
	 * filesystem.
	 * 
	 * @param avroName The string to be sanitized.
	 */
	public static String sanitizeAvroIdentifierToBaseFilename(String avroName) {
		// NOTE: Sanitizing all slashes and periods so they cannot be used to walk directories on read/write
		return avroName.replaceAll("[^\\w\\-\\_]", "_");
	}
}
