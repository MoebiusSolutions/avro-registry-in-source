import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.example.Bed;
import com.example.BedSize;
import com.example.House;
import com.example.Room;
import com.github.moebiussolutions.avro_registry_in_source.AvroJsonLoader;

public class LoaderTest {

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

		// Setup a sample object
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
		assertEquals(1, house.getRooms().get(0).getBeds().size());
		assertEquals(BedSize.KING, house.getRooms().get(0).getBeds().get(0).getSize());
		assertEquals(2, house.getRooms().get(1).getBeds().size());
		assertEquals(BedSize.QUEEN, house.getRooms().get(1).getBeds().get(0).getSize());
		assertEquals(BedSize.TWIN, house.getRooms().get(1).getBeds().get(1).getSize());
	}

}
