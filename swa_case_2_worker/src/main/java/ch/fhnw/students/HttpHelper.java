package ch.fhnw.students;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Zentraler HTTP-Client für alle Worker.
 * Kapselt die HTTP-Kommunikation und gibt ein JSONObject zurück,
 * das immer ein Feld "statusCode" enthält.
 */
public class HttpHelper {

    private static final Logger LOG = Logger.getLogger(HttpHelper.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Sendet einen POST-Request mit JSON-Body (ohne zusätzliche Header).
     */
    public static JSONObject post(String url, JSONObject body) {
        return post(url, body, Collections.emptyMap());
    }

    /**
     * Sendet einen POST-Request mit JSON-Body und zusätzlichen Headern.
     *
     * @param headers Zusätzliche HTTP-Header (z.B. X-Process-Instance-Id).
     * @return JSONObject mit mindestens dem Feld "statusCode".
     *         Bei HTTP 2xx zusätzlich alle Felder der API-Antwort.
     * @throws RuntimeException bei Netzwerk- oder Verbindungsfehlern.
     */
    public static JSONObject post(String url, JSONObject body, Map<String, String> headers) {
        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build()) {

            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));

            // Zusätzliche Header setzen
            for (Map.Entry<String, String> h : headers.entrySet()) {
                reqBuilder.header(h.getKey(), h.getValue());
            }

            HttpRequest request = reqBuilder.build();

            LOG.fine("HTTP POST " + url + " — Body: " + body);

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            LOG.fine("HTTP Response " + statusCode + " — Body: " + response.body());

            JSONObject result;
            if (response.body() != null && !response.body().isBlank()
                    && response.body().trim().startsWith("{")) {
                result = new JSONObject(response.body());
            } else {
                result = new JSONObject();
            }
            result.put("statusCode", statusCode);
            return result;

        } catch (URISyntaxException e) {
            throw new RuntimeException("Ungültige URL: " + url, e);
        } catch (IOException e) {
            throw new RuntimeException("Verbindungsfehler: " + url + " — " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request unterbrochen: " + url, e);
        }
    }
}
