package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Uygulama içi güncelleme: sürüm numarası ve GitHub sürüm listesinden seçim. */
public class YoldaUpdateTest {
	private static final String JS = "[{\"tag_name\":\"v2-226\",\"draft\":true,\"assets\":[{\"name\":\"YoldaV2-226.apk\","
			+ "\"browser_download_url\":\"https://github.com/x/226.apk\",\"size\":1}]},"
			+ "{\"tag_name\":\"v2-225\",\"assets\":[{\"name\":\"YoldaV2-225.apk\",\"browser_download_url\":\"https://github.com/x/225.apk\","
			+ "\"size\":102760448}]},"
			+ "{\"tag_name\":\"v2-224\",\"assets\":[{\"name\":\"YoldaV2-224.apk\",\"browser_download_url\":\"https://github.com/x/224.apk\","
			+ "\"size\":1}]},{\"tag_name\":\"v2-227\",\"assets\":[]}]";

	@Test
	public void numara() {
		assertEquals(224, YoldaUpdate.buildNumber("2.0.2-yolda.224"));
		assertEquals(0, YoldaUpdate.buildNumber("2.0.2"));
		assertEquals(0, YoldaUpdate.buildNumber(null));
		assertEquals(225, YoldaUpdate.tagNumber("v2-225"));
		assertEquals(-1, YoldaUpdate.tagNumber("v1.0"));
		org.junit.Assert.assertFalse("numarasız derlemede açılışta sorulmaz", YoldaUpdate.autoCheck(0));
		org.junit.Assert.assertTrue(YoldaUpdate.autoCheck(227));
	}

	@Test
	public void secim() throws Exception {
		// taslak (226) ve APK'sız (227) atlanır; en yeni APK'lı 225
		assertArrayEquals(new String[]{"v2-225", "https://github.com/x/225.apk", "98"}, YoldaUpdate.pick(JS, 224));
		assertNull("güncel", YoldaUpdate.pick(JS, 225));
		assertEquals("v2-225", YoldaUpdate.pick(JS, 0)[0]);
	}
}
