package net.cmdalias;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftCompatibilityTest {
	@ParameterizedTest
	@ValueSource(strings = {"26.4-snapshot-6", "26.4-snapshot-7", "26.4-snapshot-10",
		"26.4-pre-1", "26.4-rc-1", "26.4"})
	void acceptsSnapshotSixThroughFinalRelease(String gameVersion) throws Exception {
		assertTrue(supports(gameVersion), gameVersion);
	}

	@ParameterizedTest
	@ValueSource(strings = {"26.3", "26.4-snapshot-1", "26.4-snapshot-5",
		"26.4.1-pre-1", "26.4.1", "26.5-snapshot-1", "26.5", "27.1"})
	void rejectsEarlierSnapshotsAndVersionsAfterFinalRelease(String gameVersion) throws Exception {
		assertFalse(supports(gameVersion), gameVersion);
	}

	private static boolean supports(String gameVersion) throws Exception {
		Version version = Version.parse(McVersionLookup.normalizeVersion(gameVersion, McVersionLookup.getRelease(gameVersion)));
		var resources = MinecraftCompatibilityTest.class.getClassLoader().getResources("fabric.mod.json");
		while (resources.hasMoreElements()) {
			try (var reader = new InputStreamReader(resources.nextElement().openStream(), StandardCharsets.UTF_8)) {
				var metadata = JsonParser.parseReader(reader).getAsJsonObject();
				if ("cmdalias".equals(metadata.get("id").getAsString())) {
					return VersionPredicate.parse(metadata.getAsJsonObject("depends").get("minecraft").getAsString()).test(version);
				}
			}
		}
		throw new AssertionError("CMDAlias metadata is missing from the test classpath");
	}
}
