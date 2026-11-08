package com.minihome.tv;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** 天气: ip-api 定位(中文城市+经纬度,失败用固定城市兜底) -> open-meteo -> 中文一行; 失败时保留旧显示不清空 */
public class WeatherTask extends Thread {

    private static final String FALLBACK_CITY = "北京";
    private static final double FALLBACK_LAT = 39.9042;
    private static final double FALLBACK_LON = 116.4074;

    private final Activity act;
    private final TextView tv;

    public WeatherTask(Activity a, TextView t) {
        act = a;
        tv = t;
    }

    @Override
    public void run() {
        String city = FALLBACK_CITY;
        double lat = FALLBACK_LAT;
        double lon = FALLBACK_LON;
        if (netOk()) {
            try {
                JSONObject loc = new JSONObject(
                        get("http://ip-api.com/json/?lang=zh-CN&fields=status,city,lat,lon"));
                if ("success".equals(loc.optString("status"))) {
                    city = loc.optString("city", FALLBACK_CITY);
                    lat = loc.optDouble("lat", FALLBACK_LAT);
                    lon = loc.optDouble("lon", FALLBACK_LON);
                }
            } catch (Throwable ignored) {
            }
        }
        String text = "";
        if (netOk()) {
            try {
                JSONObject w = new JSONObject(get(
                        "http://api.open-meteo.com/v1/forecast?latitude="
                                + lat + "&longitude=" + lon + "&current_weather=true"));
                JSONObject cw = w.optJSONObject("current_weather");
                if (cw != null) {
                    int code = cw.optInt("weathercode", -1);
                    double temp = cw.optDouble("temperature");
                    text = city + " " + condText(code) + " " + Math.round(temp) + "°C";
                }
            } catch (Throwable ignored) {
                text = "";
            }
        }
        if (text.length() > 0) {
            final String result = text;
            act.runOnUiThread(new UiUpdate(tv, result));
        }
    }

    private String get(String url) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", "curl/8.0");
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            if (c != null) {
                try { c.disconnect(); } catch (Throwable ignored) {
                }
            }
        }
    }

    /** WMO 天气代码 -> 中文 */
    private static String condText(int code) {
        if (code == 0) return "晴";
        if (code == 1) return "大部晴";
        if (code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code >= 51 && code <= 57) return "毛毛雨";
        if (code == 61 || code == 80) return "小雨";
        if (code == 63 || code == 81) return "中雨";
        if (code == 65 || code == 82) return "大雨";
        if (code == 66 || code == 67) return "冻雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code == 85 || code == 86) return "阵雪";
        if (code >= 95) return "雷雨";
        return "";
    }

    private boolean netOk() {
        try {
            ConnectivityManager cm = (ConnectivityManager) act.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo n = cm.getActiveNetworkInfo();
            return n != null && n.isConnected();
        } catch (Throwable t) {
            return false;
        }
    }
}
