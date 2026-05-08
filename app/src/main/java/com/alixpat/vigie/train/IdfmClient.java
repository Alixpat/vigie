package com.alixpat.vigie.train;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Client HTTP synchrone pour les 5 endpoints PRIM IDFM utilisés par l'onglet Train.
 *
 * Les méthodes retournent le corps JSON brut en String — le parsing reste dans
 * l'appelant (TrainFragment a sa propre logique métier par endpoint).
 *
 * À utiliser depuis un thread de fond ; le client ne fait pas de retry, c'est
 * à l'appelant de boucler sur {@link HttpException} (regarde le code) ou
 * {@link IOException}.
 */
public final class IdfmClient {

    private static final String BASE_URL = "https://prim.iledefrance-mobilites.fr/marketplace";
    private static final int DEFAULT_TIMEOUT_MS = 15_000;
    /** estimated-timetable retourne un gros payload (toute la ligne) : timeout étendu. */
    private static final int LARGE_PAYLOAD_TIMEOUT_MS = 20_000;

    private final String lineRef;          // ex. "STIF:Line::C01736:" (Ligne N)
    private final String navitiaLineId;    // ex. "line:IDFM:C01736"

    public IdfmClient(String lineRef, String navitiaLineId) {
        this.lineRef = lineRef;
        this.navitiaLineId = navitiaLineId;
    }

    public String fetchGeneralMessage(String token) throws IOException {
        return fetch(BASE_URL + "/general-message?LineRef=" + lineRef, token, DEFAULT_TIMEOUT_MS);
    }

    public String fetchStopMonitoring(String token, String stopRef) throws IOException {
        return fetch(buildStopMonitoringUrl(stopRef), token, DEFAULT_TIMEOUT_MS);
    }

    public String fetchEstimatedTimetable(String token) throws IOException {
        return fetch(BASE_URL + "/estimated-timetable?LineRef=" + lineRef, token,
                LARGE_PAYLOAD_TIMEOUT_MS);
    }

    public String fetchLineReports(String token) throws IOException {
        return fetch(BASE_URL + "/v2/navitia/lines/" + encode(navitiaLineId) + "/line_reports",
                token, DEFAULT_TIMEOUT_MS);
    }

    public String fetchStopPointsDiscovery(String token) throws IOException {
        return fetch(BASE_URL + "/v2/navitia/lines/" + encode(navitiaLineId) + "/stop_points?count=100",
                token, DEFAULT_TIMEOUT_MS);
    }

    /** Pour les tests : expose la construction d'URL stop-monitoring. */
    String buildStopMonitoringUrl(String stopRef) {
        return BASE_URL + "/stop-monitoring"
                + "?MonitoringRef=" + encode(stopRef)
                + "&LineRef=" + encode(lineRef);
    }

    private static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 est garanti par la plateforme, ne devrait jamais arriver.
            throw new IllegalStateException(e);
        }
    }

    private String fetch(String url, String token, int timeoutMs) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("apikey", token);
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_OK) {
                return readBody(connection.getInputStream());
            }
            String errorBody = "";
            if (connection.getErrorStream() != null) {
                try {
                    errorBody = readBody(connection.getErrorStream());
                } catch (IOException ignored) {}
            }
            throw new HttpException(code, errorBody, url);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBody(java.io.InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    /**
     * Levée quand le serveur répond avec un code != 200. Sous-classe d'IOException
     * pour rester compatible avec les call sites qui n'attrapent que IOException.
     * L'appelant peut consulter {@link #code} pour décider d'un retry (typiquement
     * sur les codes 5xx).
     */
    public static final class HttpException extends IOException {
        public final int code;
        public final String body;
        public final String url;

        public HttpException(int code, String body, String url) {
            super("HTTP " + code + " " + url);
            this.code = code;
            this.body = body;
            this.url = url;
        }

        public boolean isServerError() {
            return code >= 500 && code < 600;
        }
    }
}
