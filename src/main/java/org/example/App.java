package org.example;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.IOException;

public class App {

    public static void main(String[] args) {
        String uri = null;

        // Sample URLs
        //uri = "https://www.mapama.gob.es/"; // Working
        uri = "https://sig.mapama.gob.es/Docs/PDFServiciosProd2/EVAL_VO_BaP.pdf"; // Not working PKIX error
        //uri = "https://b5m.gipuzkoa.eus/inspire/wfs/gipuzkoa_wfs_ad?service=WFS&request=GetCapabilities";  // Not working PKIX error
        //uri = "https://wms.mapama.gob.es/"; // Not working PKIX error

        System.out.println("Creating connection");
        HttpClientConnectionManager connManager = ConnectionManager.createConnectionManager();
        HttpClientBuilder hcb = ConnectionManager.createBuilder(/*connManager*/);

        try (CloseableHttpClient hc = hcb.build()) {
            System.out.println("Testing URL: " + uri);
            HttpUriRequest req = new HttpGet(uri);
            CloseableHttpResponse resp = hc.execute(req);
            System.out.println("Completed successfully: " + resp.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
