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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.google.common.base.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class AvroJsonLoader {

	public static final String AVRO_HEADER_TYPE = "avroType";
	public static final String AVRO_HEADER_FINGERPRINT = "avroVer";
	public static final String AVRO_HEADER_DATA= "avroData";

	// TODO [rkenney]: Remove debug
	private StopWatch fromJsonUnwrap = new StopWatch();
	{
		fromJsonUnwrap.start(); fromJsonUnwrap.suspend();
	}
	private StopWatch fromJsonLoadSchema = new StopWatch();
	{
		fromJsonLoadSchema.start(); fromJsonLoadSchema.suspend();
	}
	private StopWatch fromJsonCreateSpecificDatumReader = new StopWatch();
	{
		fromJsonCreateSpecificDatumReader.start(); fromJsonCreateSpecificDatumReader.suspend();
	}
	private StopWatch fromJsonCreateDecoder = new StopWatch();
	{
		fromJsonCreateDecoder.start(); fromJsonCreateDecoder.suspend();
	}
	private StopWatch fromJsonDecode = new StopWatch();
	{
		fromJsonDecode.start(); fromJsonDecode.suspend();
	}
	public void printTiming() {
		System.out.printf("* fromJsonUnwrap: %s%n", fromJsonUnwrap.getTime());
		System.out.printf("* fromJsonLoadSchema: %s%n", fromJsonLoadSchema.getTime());
		System.out.printf("* fromJsonCreateSpecificDatumReader: %s%n", fromJsonCreateSpecificDatumReader.getTime());
		System.out.printf("* fromJsonFullDecode: %s%n", fromJsonCreateDecoder.getTime());
		System.out.printf("* fromJsonDecode: %s%n", fromJsonDecode.getTime());
	}
	
	private static class SchemaLoader implements Function<String, Schema> {
		@Override
		public Schema apply(String schemaPath) {
			try (InputStream in = AvroJsonLoader.class.getResourceAsStream(schemaPath)) {
				if (in == null) {
					throw new RuntimeException("Failed to load schema from resource ["+schemaPath+"]");
				}

				// TODO [rkenney]: Remove debug
//				System.out.println("PARSING!");

				return new Schema.Parser().parse(in);
			} catch (RuntimeException | IOException e){
				throw new RuntimeException("Failed to load resource file ["+schemaPath+"]", e);
			}
		}
	}
	private final ThreadLocal<LoadingCache<String, Schema>> schemaCache = new ThreadLocal<LoadingCache<String, Schema>>() {
		@Override protected LoadingCache<String, Schema> initialValue() {
			return CacheBuilder.newBuilder().build(CacheLoader.from(new SchemaLoader()));
		}
	};

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

		fromJsonUnwrap.resume();

		// Parse as generic JSON, extracting Avro schema info
		String writerSchemaType;
		String writerSchemaVersion;
		String mainJsonData;
		try (StringReader reader = new StringReader(json)) {
			try (JsonReader jsonReader = Json.createReader(reader)) {
				JsonObject root = jsonReader.readObject();
				root.getString(AVRO_HEADER_TYPE);
				root.getString(AVRO_HEADER_FINGERPRINT);
				writerSchemaType = root.getString(AVRO_HEADER_TYPE);
				writerSchemaVersion = root.getString(AVRO_HEADER_FINGERPRINT);
				if (StringUtils.isBlank(writerSchemaType) || StringUtils.isBlank(writerSchemaVersion)) {
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
		if (!requestedSchemaType.equals(writerSchemaType)) {
			throw new RuntimeException(
					"Cannot load JSON message of type ["+writerSchemaType+"] as ["+requestedSchemaType+"]");
		}

		fromJsonUnwrap.suspend();

		fromJsonLoadSchema.resume();

		// Load Avro schema (potentially older than the current schema)
		String oldSchemaPath = String.format("%s/%s_%s.avsc", this.schemaRegistryResourcePath, writerSchemaType, writerSchemaVersion);
		Schema oldSchema = schemaCache.get().getUnchecked(oldSchemaPath);

		fromJsonLoadSchema.suspend();

		// Load instantiate the Avro POJO
		K result;
		JsonDecoder decoder;
		try {

			fromJsonCreateDecoder.resume();

			decoder = DecoderFactory.get().jsonDecoder(oldSchema, mainJsonData);

			fromJsonCreateDecoder.suspend();

			fromJsonCreateSpecificDatumReader.resume();

			SpecificDatumReader<K> reader = new SpecificDatumReader<K>(oldSchema, avroPojo.getSchema());

			fromJsonCreateSpecificDatumReader.suspend();

			fromJsonDecode.resume();

			result = reader.read(null, decoder);

			fromJsonDecode.suspend();

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
