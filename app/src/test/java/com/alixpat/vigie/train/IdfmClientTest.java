package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests sans serveur HTTP : on couvre la construction des URLs (qui contient
 * encoding + line refs) et le contrat de HttpException. Le chemin HTTP réel
 * est exercé par les call sites en intégration (TrainFragment) — pas mocké ici.
 */
public class IdfmClientTest {

    private static final String LINE_REF = "STIF:Line::C01736:";
    private static final String NAVITIA_LINE_ID = "line:IDFM:C01736";

    private final IdfmClient client = new IdfmClient(LINE_REF, NAVITIA_LINE_ID);

    @Test
    public void stopMonitoringUrlEncodesBothRefs() {
        String url = client.buildStopMonitoringUrl("STIF:StopArea:SP:43111:");
        assertEquals(
                "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring"
                        + "?MonitoringRef=STIF%3AStopArea%3ASP%3A43111%3A"
                        + "&LineRef=STIF%3ALine%3A%3AC01736%3A",
                url);
    }

    @Test
    public void stopMonitoringUrlEncodesAnotherStop() {
        String url = client.buildStopMonitoringUrl("STIF:StopArea:SP:43221:");
        assertTrue(url.startsWith(
                "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring"));
        // Le stop doit être encodé exactement une fois (pas de double-encoding).
        assertTrue(url.contains("MonitoringRef=STIF%3AStopArea%3ASP%3A43221%3A"));
        // Et pas brut.
        assertFalse(url.contains("MonitoringRef=STIF:StopArea:SP:43221:"));
    }

    @Test
    public void httpExceptionMessageContainsCodeAndUrl() {
        IdfmClient.HttpException e = new IdfmClient.HttpException(
                503, "Service Unavailable",
                "https://example.com/foo");
        assertEquals(503, e.code);
        assertEquals("Service Unavailable", e.body);
        assertTrue(e.getMessage().contains("503"));
        assertTrue(e.getMessage().contains("https://example.com/foo"));
    }

    @Test
    public void httpException_isServerError_500to599() {
        assertTrue(new IdfmClient.HttpException(500, "", "u").isServerError());
        assertTrue(new IdfmClient.HttpException(503, "", "u").isServerError());
        assertTrue(new IdfmClient.HttpException(599, "", "u").isServerError());
    }

    @Test
    public void httpException_isServerError_falseForClientErrors() {
        assertFalse(new IdfmClient.HttpException(400, "", "u").isServerError());
        assertFalse(new IdfmClient.HttpException(401, "", "u").isServerError());
        assertFalse(new IdfmClient.HttpException(404, "", "u").isServerError());
        assertFalse(new IdfmClient.HttpException(429, "", "u").isServerError());
        assertFalse(new IdfmClient.HttpException(499, "", "u").isServerError());
        assertFalse(new IdfmClient.HttpException(600, "", "u").isServerError());
    }

    @Test
    public void httpExceptionIsCatchableAsIOException() {
        // Contrat utilisé par TrainFragment: catcher HttpException en premier,
        // puis fall through vers Exception. HttpException doit donc être une
        // IOException pour rester compatible avec les call sites historiques.
        IdfmClient.HttpException e = new IdfmClient.HttpException(500, "", "u");
        assertTrue(e instanceof java.io.IOException);
    }
}
