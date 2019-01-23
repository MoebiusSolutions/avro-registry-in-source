package com.github.moebiussolutions.avro_registry_in_source;

@SuppressWarnings("serial")
public class AvroSchemaNotFoundException extends AvroJsonLoaderException {

	public AvroSchemaNotFoundException(String msg) {
		super(msg);
	}

}
