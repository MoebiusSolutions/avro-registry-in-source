package com.github.moebiussolutions.avro_registry_in_source;

import org.apache.avro.Schema;

public interface SchemaProvider {

	/**
	 * Returns a {@link Schema}, <code>null</code> if not found.
	 */
	Schema getSchema(String type, long signature);

}
