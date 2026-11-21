package me.aap.fermata.ui.fragment;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.util.Calendar;

import me.aap.fermata.ui.view.YoldaStyle;
import me.aap.utils.app.App;

/**
 * Sesli karşılama (Mola'dan uyarlandı, GPL-3.0): telefon Android Auto'ya bağlanınca günün saatine göre kısa
 * selam, isim ve havanın tek cümlesi: "Günaydın Emre. Hava 14 derece, parçalı bulutlu. İyi yolculuklar!"
 * İsteğe bağlı (varsayılan kapalı), istenirse günde bir kez. Mikrofon ve yeni izin kullanılmaz.
 */
public final class YoldaWelcome {
	static final String ON = "welcome_on", NAME = "welcome_name", ONCE = "welcome_once", LAST = "welcome_last_day",
			BIRTHDAY = "welcome_birthday";
	private static final int MAX_NAME = 24;

	private YoldaWelcome() {
	}

	/** 05-11 Günaydın, 12-17 İyi günler, 18-22 İyi akşamlar, diğer İyi geceler. */
	static String hello(int hour) {
		if ((hour >= 5) && (hour < 12)) return "Günaydın";
		if ((hour >= 12) && (hour < 18)) return "İyi günler";
		if ((hour >= 18) && (hour < 23)) return "İyi akşamlar";
		return "İyi geceler";
	}

	/** Seslendirmeye uygun isim: tek satır, işaretsiz, en çok 24 karakter. */
	static String cleanName(@Nullable String name) {
		if (name == null) return "";
		StringBuilder b = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isLetterOrDigit(c) || (c == ' ') || (c == '-') || (c == '\'') || (c == '’') || (c == '.')) b.append(c);
			else if (Character.isWhitespace(c)) b.append(' ');
		}
		String s = b.toString().trim().replaceAll(" +", " ");
		return (s.length() <= MAX_NAME) ? s : s.substring(0, MAX_NAME).trim();
	}

	/** Açıksa ve (günde bir kezse) bugün henüz selam verilmediyse. */
	static boolean shouldGreet(boolean on, boolean once, long lastDay, long today) {
		return on && (!once || (lastDay != today));
	}

	/**
	 * Özel gün cümlesi (Mola'dan): doğum günü, resmî bayramlar, 10 Kasım, yılbaşı, cuma; yoksa boş.
	 * {@code birthday}: "AA-GG" (ör. "11-21") ya da boş. {@code dow}: Calendar.DAY_OF_WEEK.
	 */
	static String specialDay(int month, int day, int dow, @Nullable String birthday) {
		String md = String.format(java.util.Locale.ROOT, "%02d-%02d", month, day);
		if ((birthday != null) && birthday.equals(md)) return "Doğum günün kutlu olsun! Bugün yol senin.";
		switch (md) {
			case "01-01":
				return "Mutlu yıllar! Yeni yılın ilk yolculuğu hayırlı olsun.";
			case "04-23":
				return "23 Nisan Ulusal Egemenlik ve Çocuk Bayramı kutlu olsun!";
			case "05-01":
				return "1 Mayıs Emek ve Dayanışma Günü kutlu olsun!";
			case "05-19":
				return "19 Mayıs Atatürk'ü Anma, Gençlik ve Spor Bayramı kutlu olsun!";
			case "07-15":
				return "15 Temmuz Demokrasi ve Milli Birlik Günü.";
			case "08-30":
				return "30 Ağustos Zafer Bayramı kutlu olsun!";
			case "10-29":
				return "Cumhuriyet Bayramı kutlu olsun!";
			case "11-10":
				return "Bugün 10 Kasım; Atatürk'ü saygı ve özlemle anıyoruz.";
			default:
				break;
		}
		if (dow == Calendar.FRIDAY) return "Cuma geldi, hafta sonu kapıda.";
		if ((dow == Calendar.SATURDAY) || (dow == Calendar.SUNDAY)) return "Hafta sonu yolculuğu keyifli geçsin.";
		return "";
	}

	/** "21.11" / "21/11" / "21 11" → "11-21"; okunamazsa boş. */
	static String parseBirthday(@Nullable String s) {
		if (s == null) return "";
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d{1,2})[./ -](\\d{1,2})\\s*$").matcher(s);
		if (!m.matches()) return "";
		int d = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2));
		if ((d < 1) || (d > 31) || (mo < 1) || (mo > 12)) return "";
		return String.format(java.util.Locale.ROOT, "%02d-%02d", mo, d);
	}

	/** Söylenecek metin; weather null ise hava cümlesi yok. */
	static String text(int hour, String name, @Nullable YoldaWeather.Now weather) {
		return text(hour, name, weather, "");
	}

	/** Söylenecek metin + özel gün cümlesi (boş olabilir). */
	static String text(int hour, String name, @Nullable YoldaWeather.Now weather, String extra) {
		StringBuilder b = new StringBuilder(hello(hour));
		if (!name.isEmpty()) b.append(' ').append(name);
		b.append('.');
		if (weather != null)
			b.append(" Hava ").append(weather.temp()).append(" derece, ").append(YoldaWeather.text(weather.code()).toLowerCase(new java.util.Locale("tr"))).append('.');
		if (!extra.isEmpty()) b.append(' ').append(extra);
		b.append(" İyi yolculuklar!");
		return b.toString();
	}

	private static SharedPreferences prefs(Context ctx) {
		return ctx.getApplicationContext().getSharedPreferences(YoldaStyle.PREFS, Context.MODE_PRIVATE);
	}

	public static boolean isOn(Context ctx) {
		return prefs(ctx).getBoolean(ON, false);
	}

	public static String name(Context ctx) {
		return cleanName(prefs(ctx).getString(NAME, ""));
	}

	public static boolean once(Context ctx) {
		return prefs(ctx).getBoolean(ONCE, true);
	}

	/** Doğum günü ("GG.AA"); geçersizse silinir. */
	public static void setBirthday(Context ctx, @Nullable String ddmm) {
		prefs(ctx).edit().putString(BIRTHDAY, parseBirthday(ddmm)).apply();
	}

	public static void set(Context ctx, boolean on, @Nullable String name, boolean once) {
		SharedPreferences.Editor e = prefs(ctx).edit().putBoolean(ON, on).putBoolean(ONCE, once);
		if (name != null) e.putString(NAME, cleanName(name));
		e.apply();
	}

	private static long today() {
		Calendar c = Calendar.getInstance();
		return c.get(Calendar.YEAR) * 1000L + c.get(Calendar.DAY_OF_YEAR);
	}

	/** Araç bağlandı: gerekiyorsa 4 sn sonra (ses yolu otursun) selam ver. */
	public static void onCarConnected(Context ctx) {
		Context app = ctx.getApplicationContext();
		SharedPreferences p = prefs(app);
		long t = today();
		if (!shouldGreet(p.getBoolean(ON, false), p.getBoolean(ONCE, true), p.getLong(LAST, -1), t)) return;
		p.edit().putLong(LAST, t).apply();
		App.get().getHandler().postDelayed(() -> {
			Calendar c = Calendar.getInstance();
			String s = text(c.get(Calendar.HOUR_OF_DAY), name(app), YoldaWeather.cachedNow(), specialDay(c.get(Calendar.MONTH) + 1,
					c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.DAY_OF_WEEK), prefs(app).getString(BIRTHDAY, "")));
			YoldaLog.add("Sesli karşılama: " + s);
			YoldaSpeak.sayAlways(app, s);
		}, 4000);
	}
}
