package com.glass.headunit;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Reads Open-Meteo's forecast and air quality answers into a Forecast. Pure, so it can be tested with sample text. */
final class ForecastParser {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH':00'", Locale.US);

    private ForecastParser() { }

    static Forecast parse(String forecastJson, String airJson) { return parse(forecastJson, airJson, 0L); }

    /**
     * nowMs above zero means "as of this moment" (for a saved forecast read again later): the hour list then starts at
     * the current hour at the place, worked out from the answer's own UTC offset. Zero uses the time inside the answer.
     * Returns null when the forecast cannot be read; a missing air quality answer only leaves it invalid.
     */
    static Forecast parse(String forecastJson, String airJson, long nowMs) {
        if (forecastJson == null || forecastJson.isEmpty()) return null;
        try {
            JSONObject o = new JSONObject(forecastJson);
            JSONObject cur = o.getJSONObject("current");
            Forecast f = new Forecast();
            f.tempC = (float) cur.getDouble("temperature_2m");
            f.feelsC = (float) cur.optDouble("apparent_temperature", f.tempC);
            f.humidity = cur.optInt("relative_humidity_2m", 0);
            f.windKmh = (float) cur.optDouble("wind_speed_10m", 0);
            f.code = cur.optInt("weather_code", 0);

            String hourStart;
            if (nowMs > 0) {
                int offset = o.optInt("utc_offset_seconds", 0);
                hourStart = HOUR.format(Instant.ofEpochMilli(nowMs).atOffset(ZoneOffset.ofTotalSeconds(offset)));
            } else {
                hourStart = cur.getString("time").substring(0, 13) + ":00";
            }

            JSONObject h = o.getJSONObject("hourly");
            JSONArray times = h.getJSONArray("time"), temps = h.getJSONArray("temperature_2m");
            JSONArray codes = h.optJSONArray("weather_code"), rain = h.optJSONArray("precipitation_probability");
            int start = 0;
            for (int i = 0; i < times.length(); i++) {
                if (times.getString(i).compareTo(hourStart) >= 0) { start = i; break; }
            }
            for (int i = start; i < times.length() && f.hours.size() < 24; i++) {
                int hh = Integer.parseInt(times.getString(i).substring(11, 13));
                f.hours.add(new Forecast.Hour(hourLabel(hh), (float) temps.optDouble(i, 0),
                        codes == null ? 0 : codes.optInt(i, 0), rain == null ? 0 : rain.optInt(i, 0)));
            }

            JSONObject d = o.getJSONObject("daily");
            JSONArray dates = d.getJSONArray("time");
            JSONArray dCodes = d.optJSONArray("weather_code"), hi = d.getJSONArray("temperature_2m_max"), lo = d.getJSONArray("temperature_2m_min");
            JSONArray dRain = d.optJSONArray("precipitation_probability_max");
            for (int i = 0; i < dates.length() && i < 7; i++) {
                f.days.add(new Forecast.Day(i == 0 ? "Today" : dayName(dates.getString(i)), dCodes == null ? 0 : dCodes.optInt(i, 0),
                        (float) hi.optDouble(i, 0), (float) lo.optDouble(i, 0), dRain == null ? 0 : dRain.optInt(i, 0)));
            }

            if (airJson != null && !airJson.isEmpty()) {
                try {
                    JSONObject a = new JSONObject(airJson).getJSONObject("current");
                    if (!a.isNull("us_aqi")) {
                        f.air.valid = true;
                        f.air.usAqi = a.getInt("us_aqi");
                        f.air.pm25 = (float) a.optDouble("pm2_5", 0);
                        f.air.pm10 = (float) a.optDouble("pm10", 0);
                    }
                } catch (Exception ignored) { /* air quality is optional */ }
            }
            return f;
        } catch (Exception e) {
            return null;
        }
    }

    /** 0 gives "12 AM", 13 gives "1 PM". */
    static String hourLabel(int hour) {
        int h12 = hour % 12 == 0 ? 12 : hour % 12;
        return h12 + (hour < 12 ? " AM" : " PM");
    }

    private static String dayName(String isoDate) {
        DayOfWeek w = LocalDate.parse(isoDate).getDayOfWeek();
        return w.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}
