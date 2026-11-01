package net.cmdalias;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckerTest {
	@Test
	void usesOriginalMinecraftIdForModrinthInsteadOfNormalizedLoaderVersion() {
		SharedConstants.tryDetectVersion();
		assertEquals(System.getProperty("cmdalias.minecraftVersion"), UpdateChecker.currentMinecraftVersion().orElseThrow());
	}

	@Test
	void selectsOnlyFabricReleaseForRawSnapshotId() {
		var releases = JsonParser.parseString("""
			[
			  {"version_number":"1.1.1","version_type":"release","date_published":"2026-11-01T10:00:00Z",
			   "loaders":["fabric"],"game_versions":["26.4-snapshot-6"]},
			  {"version_number":"1.2.0","version_type":"release","date_published":"2026-11-02T10:00:00Z",
			   "loaders":["fabric"],"game_versions":["26.4-alpha.6"]},
			  {"version_number":"1.3.0","version_type":"release","date_published":"2026-11-03T10:00:00Z",
			   "loaders":["neoforge"],"game_versions":["26.4-snapshot-6"]}
			]
			""").getAsJsonArray();
		assertEquals("1.1.1", UpdateChecker.findLatestVersion(releases, "26.4-snapshot-6").orElseThrow().versionNumber());
	}

	@Test
	void doesNotFallBackToAnIncompatibleMinecraftRelease() {
		var releases = JsonParser.parseString("""
			[{"version_number":"1.2.0","version_type":"release","date_published":"2026-11-01T10:00:00Z",
			  "loaders":["fabric"],"game_versions":["26.4"]}]
			""").getAsJsonArray();
		assertTrue(UpdateChecker.findLatestVersion(releases, "26.4-snapshot-6").isEmpty());
	}
}
