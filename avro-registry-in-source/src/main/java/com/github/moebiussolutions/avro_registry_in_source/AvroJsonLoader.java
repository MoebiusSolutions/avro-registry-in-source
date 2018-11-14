package com.github.moebiussolutions.avro_registry_in_source;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.avro.Schema;
import org.apache.avro.SchemaNormalization;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.io.JsonDecoder;
import org.apache.avro.io.JsonEncoder;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

public class AvroJsonLoader {

	public static final String AVRO_HEADER_TYPE = "avroType";
	public static final String AVRO_HEADER_FINGERPRINT = "avroVer";
	public static final String AVRO_HEADER_DATA= "avroData";

	private final String schemaRegistryResourcePath;

	public AvroJsonLoader(String schemaRegistryResourcePath) {
		this.schemaRegistryResourcePath = schemaRegistryResourcePath;
	}

	/**
	 * Intantiates an Avro POJO from a JSON string that was written by
	 * {@link #toJson(SpecificRecordBase, OutputStream, boolean)}, which ensures
	 * that the JSON is wrapped with metadata about the Avro schema.
	 * 
	 * @param json
	 *            The JSON to parse.
	 * @param avroPojo
	 *            The Avro POJO type to return. The provided object is NOT
	 *            modified--only used for typing information.
	 * @param <K>
	 *            The POJO type to return.
	 * @return A new instance of the Avro POJO.
	 */
	public <K extends SpecificRecordBase> K fromJson(String json, final K avroPojo) {

		// Parse as generic JSON, extracting Avro schema info
		String schemaType;
		String schemaVersion;
		String mainJsonData;
		try (StringReader reader = new StringReader(json)) {
			try (JsonReader jsonReader = Json.createReader(reader)) {
				JsonObject root = jsonReader.readObject();
				root.getString(AVRO_HEADER_TYPE);
				root.getString(AVRO_HEADER_FINGERPRINT);
				schemaType = root.getString(AVRO_HEADER_TYPE);
				schemaVersion = root.getString(AVRO_HEADER_FINGERPRINT);
				if (StringUtils.isBlank(schemaType) || StringUtils.isBlank(schemaVersion)) {
					throw new RuntimeException("JSON message does not contain Avro header(s) "
							+ "("+AVRO_HEADER_TYPE+", "+AVRO_HEADER_FINGERPRINT+")");
				}
				JsonObject data = root.getJsonObject(AVRO_HEADER_DATA);
				if (null == data) {
					throw new RuntimeException("JSON message does not contain Avro body "
							+ "("+AVRO_HEADER_DATA+")");
				}
				mainJsonData = data.toString();
			}
		}
		String requestedSchemaType = avroPojo.getSchema().getName();
		if (!requestedSchemaType.equals(schemaType)) {
			throw new RuntimeException(
					"Cannot load JSON message of type ["+schemaType+"] as ["+requestedSchemaType+"]");
		}

		// Load Avro schema (potentially older than the current schema)
		Schema oldSchema; 
		String oldSchemaPath = String.format("%s/%s_%s.avsc", this.schemaRegistryResourcePath, schemaType, schemaVersion);
		// TODO [rkenney]: Cache the schema object
		try (InputStream in = AvroJsonLoader.class.getResourceAsStream(oldSchemaPath)) {
			if (in == null) {
				throw new RuntimeException("Failed to load schema from resource ["+oldSchemaPath+"]");
			}
			oldSchema = new Schema.Parser().parse(in);
		} catch (RuntimeException | IOException e){
			throw new RuntimeException("Failed to load resource file ["+oldSchemaPath+"]", e);
		}

		// Load instantiate the Avro POJO
		K result;
		JsonDecoder decoder;
		try {
			decoder = DecoderFactory.get().jsonDecoder(oldSchema, mainJsonData);
			SpecificDatumReader<K> reader = new SpecificDatumReader<K>(oldSchema, avroPojo.getSchema());
			result = reader.read(null, decoder);
		} catch (RuntimeException | IOException e) {
			throw new RuntimeException("Failed to decode json message to as ["+requestedSchemaType+"]", e);
		}

		return result;
	}

	/**
	 * Serializes an Avro POJO to a JSON format that is wrapped by schema metadata
	 * and can be deserialized with {@link #fromJson(String, SpecificRecordBase)}.
	 * 
	 * @param avroPojo
	 *            The Avro POJO to serialize.
	 * @param pretty
	 *            Whether or not to include newlines/indenting in the resulting
	 *            JSON.
	 * @param <K>
	 *            The POJO type to return.
	 * @return A JSON string.
	 */
	public <K extends SpecificRecordBase> String toJson(K avroPojo, boolean pretty) {
		String json;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			new AvroJsonLoader("/avro-registry").toJson(avroPojo, out, pretty);
			json = out.toString();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write to in-memory buffer (shouldn't happen)", e);
		}
		return json;
	}

	/**
	 * Serializes an Avro POJO to a JSON format that is wrapped by schema metadata
	 * and can be deserialized with {@link #fromJson(String, SpecificRecordBase)}.
	 * 
	 * @param avroPojo
	 *            The Avro POJO to serialize.
	 * @param out
	 *            The stream to write to.
	 * @param pretty
	 *            Whether or not to include newlines/indenting in the resulting
	 *            JSON.
	 * @param <K>
	 *            The POJO type to return.
	 */
	public <K extends SpecificRecordBase> void toJson(K avroPojo, OutputStream out, boolean pretty) {
		
		final String MSG_HOLDER = "##MSG##"; 
		final Pattern MSG_HOLDER_PATTERN = Pattern.compile(Pattern.quote("\""+MSG_HOLDER+"\""));
		
		// TODO [rkenney]: Cache fingerprints
		long fp = SchemaNormalization.parsingFingerprint64(avroPojo.getSchema());
		String avroWrapper = Json.createObjectBuilder()
			.add(AVRO_HEADER_TYPE, avroPojo.getSchema().getName())
			.add(AVRO_HEADER_FINGERPRINT, ""+fp)
			.add(AVRO_HEADER_DATA, MSG_HOLDER).build().toString();
		
		Matcher matcher = MSG_HOLDER_PATTERN.matcher(avroWrapper);
		if (!matcher.find()) {
			throw new RuntimeException("Missing pattern. This should not happen.");
		}
		String headerPrefix = avroWrapper.substring(0, matcher.start());
		String headerSuffix = avroWrapper.substring(matcher.end(), avroWrapper.length());
		try {
			IOUtils.write(headerPrefix, out, StandardCharsets.UTF_8);
			if (pretty) {
				IOUtils.write(System.lineSeparator(), out, StandardCharsets.UTF_8);
			}
			out.flush();
			@SuppressWarnings("unchecked")
			DatumWriter<K> writer = new SpecificDatumWriter<>((Class<K>) avroPojo.getClass());
			JsonEncoder jsonEncoder;
			jsonEncoder = EncoderFactory.get().jsonEncoder(avroPojo.getSchema(), out, pretty);
			writer.write(avroPojo, jsonEncoder);
			jsonEncoder.flush();
			if (pretty) {
				IOUtils.write(System.lineSeparator(), out, StandardCharsets.UTF_8);
			}
			IOUtils.write(headerSuffix, out, StandardCharsets.UTF_8);
			out.flush();
		} catch (IOException e) {
			throw new RuntimeException("Failed to write avro pojo to json", e);
		}
	}
}
