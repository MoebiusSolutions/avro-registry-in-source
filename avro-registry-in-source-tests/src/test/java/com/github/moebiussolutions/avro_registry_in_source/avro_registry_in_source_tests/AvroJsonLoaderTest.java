package com.github.moebiussolutions.avro_registry_in_source.avro_registry_in_source_tests;


import static org.junit.Assert.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.example.Bed;
import com.example.BedFirmness;
import com.example.BedSize;
import com.example.House;
import com.example.Room;
import com.github.moebiussolutions.avro_registry_in_source.AvroJsonLoader;
import com.google.common.base.StandardSystemProperty;

public class AvroJsonLoaderTest {

	/**
	 * Verifies the ability to serialize to/from POJO, with "pretty json" enabled.
	 */
	@Test
	public void testToJsonFromJson_WithPretty() throws Exception {
		doTestToJsonFromJson(true);
	}

	/**
	 * Verifies the ability to serialize to/from POJO, with "pretty json" disabled.
	 */
	@Test
	public void testToJsonFromJson_WithoutPretty() throws Exception {
		doTestToJsonFromJson(false);
	}

	public void doTestToJsonFromJson(boolean prettyJson) throws Exception {

		AvroJsonLoader loader = new AvroJsonLoader("/avro-registry");

		// Build a POJO
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

		// Convert back to POJO
		house = loader.fromJson(json, house);

		// Verify resulting structure
		assertEquals(2, house.getRooms().size());
		Room room = house.getRooms().get(0);
		assertEquals(1, room.getBeds().size());
		Bed bed = room.getBeds().get(0);
		assertEquals(BedSize.KING, bed.getSize());
		// ... Including default values
		assertEquals(BedFirmness.HARD, bed.getFirmness());
		room = house.getRooms().get(1);
		assertEquals(2, room.getBeds().size());
		bed = room.getBeds().get(0);
		assertEquals(BedSize.QUEEN, bed.getSize());
		// ... Including default values
		assertEquals(BedFirmness.HARD, bed.getFirmness());
		bed = room.getBeds().get(1);
		assertEquals(BedSize.TWIN, bed.getSize());
		// ... Including default values
		assertEquals(BedFirmness.HARD, bed.getFirmness());
	}

	@Test
	public void testFromJson_FromOldSchema() throws Exception {

		String json;
		try (InputStream in = AvroJsonLoaderTest.class.getResourceAsStream("/LoaderTest_house-before-firmness.json")) {
			json = IOUtils.toString(in, StandardCharsets.UTF_8);
		}

		int cycles = 10000;
		AvroJsonLoader loader = new AvroJsonLoader("/avro-registry");
		long before = System.currentTimeMillis();
		for (int i=0; i<cycles; i++) {
			House house = loader.fromJson(json, new House());
		}
		long after = System.currentTimeMillis();
		System.out.println("Duration: "+(after - before));
		loader.printTiming();

//		before = System.currentTimeMillis();
//		for (int i=0; i<cycles; i++) {
//			loader = new AvroJsonLoader("/avro-registry");
//			House house = loader.fromJson(json, new House());
//		}
//		after = System.currentTimeMillis();
//		System.out.println("Duration: "+(after - before));

//		// Verify resulting structure
//		assertEquals(2, house.getRooms().size());
//		Room room = house.getRooms().get(0);
//		assertEquals(1, room.getBeds().size());
//		Bed bed = room.getBeds().get(0);
//		assertEquals(BedSize.KING, bed.getSize());
//		// ... Including values for schema-evolved fields
//		assertEquals(BedFirmness.HARD, bed.getFirmness());
//		room = house.getRooms().get(1);
//		assertEquals(2, room.getBeds().size());
//		bed = room.getBeds().get(0);
//		assertEquals(BedSize.QUEEN, bed.getSize());
//		// ... Including values for schema-evolved fields
//		assertEquals(BedFirmness.HARD, bed.getFirmness());
//		bed = room.getBeds().get(1);
//		assertEquals(BedSize.TWIN, bed.getSize());
//		// ... Including values for schema-evolved fields
//		assertEquals(BedFirmness.HARD, bed.getFirmness());
	}
}
