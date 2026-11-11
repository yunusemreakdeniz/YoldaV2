package me.aap.fermata.media.yt;

import androidx.annotation.NonNull;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** NewPipeExtractor için ek kütüphanesiz HTTP indirici (HttpURLConnection). */
final class YtDownloader extends Downloader {
	static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

	@Override
	public Response execute(@NonNull Request request) throws IOException, ReCaptchaException {
		HttpURLConnection c = (HttpURLConnection) new URL(request.url()).openConnection();
		try {
			c.setConnectTimeout(15000);
			c.setReadTimeout(20000);
			c.setInstanceFollowRedirects(true);
			c.setRequestMethod(request.httpMethod());
			c.setRequestProperty("User-Agent", UA);
			Map<String, List<String>> headers = request.headers();
			if (headers != null) {
				for (Map.Entry<String, List<String>> e : headers.entrySet()) {
					c.setRequestProperty(e.getKey(), String.join(",", e.getValue()));
				}
			}
			byte[] data = request.dataToSend();
			if (data != null) {
				c.setDoOutput(true);
				try (OutputStream out = c.getOutputStream()) {
					out.write(data);
				}
			}

			int code = c.getResponseCode();
			if (code == 429) throw new ReCaptchaException("Çok fazla istek", request.url());
			InputStream in = (code >= 400) ? c.getErrorStream() : c.getInputStream();
			String body = "";
			if (in != null) {
				try (InputStream is = in) {
					ByteArrayOutputStream bo = new ByteArrayOutputStream();
					byte[] buf = new byte[16384];
					for (int n; (n = is.read(buf)) > 0; ) bo.write(buf, 0, n);
					body = bo.toString(StandardCharsets.UTF_8.name());
				}
			}
			Map<String, List<String>> rh = new java.util.HashMap<>();
			for (Map.Entry<String, List<String>> e : c.getHeaderFields().entrySet()) {
				if (e.getKey() != null) rh.put(e.getKey(), e.getValue()); // durum satırının anahtarı null
			}
			return new Response(code, c.getResponseMessage(), rh, body, c.getURL().toString());
		} finally {
			c.disconnect();
		}
	}
}
