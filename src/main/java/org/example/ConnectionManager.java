package org.example;

import org.apache.http.ProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;

import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import org.apache.http.HeaderElement;
import org.apache.http.HeaderElementIterator;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

public class ConnectionManager {

    private static final Collection<String> defaultTargetPreferredAuthSchemes = Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST);
    private static final Collection<String> defaultProxyPreferredAuthSchemes = Collections.singletonList(AuthSchemes.BASIC);
    private static final long DEFAULT_KEEP_ALIVE_TIMEOUT_SEC = 30;

    /**
     * Returns the JDK's default X509ExtendedTrustManager, or a null trust manager if the default cannot be found.
     */
    private static X509ExtendedTrustManager getDefaultExtendedTrustManager() {
        X509ExtendedTrustManager xtm = null;
        TrustManagerFactory tmf = null;
        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            // initialize the TrustManagerFactory with the default KeyStore
            tmf.init((KeyStore) null);

            // find the X509ExtendedTrustManager in the list of registered trust managers
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509ExtendedTrustManager) {
                    xtm = (X509ExtendedTrustManager) tm;
                    break;
                }
            }
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            e.printStackTrace();
        }
        return xtm;
    }

    public static HttpClientConnectionManager createConnectionManager() {
        HttpClientConnectionManager resConnMgr = null;

        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            TrustManager[] tm = new TrustManager[]{getDefaultExtendedTrustManager()};
            sslContext.init(null, tm, null);
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        if (sslContext != null) {
            HostnameVerifier hv = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
            SSLConnectionSocketFactory scsf = new SSLConnectionSocketFactory(sslContext, hv);

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
                    .<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", scsf)
                    .build();

            // Create a connection manager with custom configuration.
            resConnMgr = new PoolingHttpClientConnectionManager(socketFactoryRegistry);

            //resConnMgr.setMaxTotal(DEFAULT_MAX_TOTAL_CONNECTION_VALUE);
            //resConnMgr.setDefaultMaxPerRoute(DEFAULT_MAX_CONNECTION_PER_ROUTE_VALUE);
            //resConnMgr.setValidateAfterInactivity(DEFAULT_CONNECTION_REVALIDATE_TIMEOUT_VALUE);
        }

        return resConnMgr;
    }

    public static HttpClientBuilder createBuilder(/*HttpClientConnectionManager connManager*/) {
        // Use custom cookie store if necessary.
        CookieStore cookieStore = new BasicCookieStore();

        //DART extracted RedirectStrategy reference code to trace redirection
        RedirectStrategy customRedirectStrategy = createCustomRedirectStrategy();
        //DART create our KeepAlive strategy
        ConnectionKeepAliveStrategy customKeepAliveStrategy = createCustomHttpKeepAliveStrategy();

        // Create an HttpClient with the given custom dependencies and configuration.
        HttpClientBuilder resBuilder = HttpClients.custom()
                //.setConnectionManager(this.getTransformer().getConnManager())
                .setDefaultCookieStore(cookieStore)
                .setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()))
                .setRedirectStrategy(customRedirectStrategy)
                .setKeepAliveStrategy(customKeepAliveStrategy)
                //DART see AuthenticationAndAuthorizationModel in Transformer class for credential and proxy settings
                //.setDefaultCredentialsProvider(buildDefaultCredentialsProvider())
                .setDefaultRequestConfig(buildDefaultRequestConfig());

        return resBuilder;
    }

    public static RequestConfig buildDefaultRequestConfig() {
        RequestConfig defaultRequestConfig = RequestConfig
                .custom()
                .setCookieSpec(CookieSpecs.DEFAULT) // since BestMatch has been deprecated
                //.setExpectContinueEnabled(true)
                .setMaxRedirects(0)
                //.setStaleConnectionCheckEnabled(true) //DART deprecated in HttpClient 4.4, use setValidateAfterInactivity() in ConnManager
                .setTargetPreferredAuthSchemes(defaultTargetPreferredAuthSchemes)
                .setProxyPreferredAuthSchemes(defaultProxyPreferredAuthSchemes)
                .build();

        return defaultRequestConfig;
    }

    //DART Implement our redirection strategy, just to trace redirections if occurs
    private static RedirectStrategy createCustomRedirectStrategy() {
        //DART extracted RedirectStrategy reference
        // Code copied from LaxRedirectStrategy, available in HttpClient > 4.2
        RedirectStrategy customRedirectStrategy = new DefaultRedirectStrategy() {
            /**
             * Redirectable methods.
             */
            private final String[] CUSTOM_REDIRECT_METHODS = new String[]{
                HttpGet.METHOD_NAME,
                //DART do NOT enable this HttpPost attribute, since in HttpClient >4.5 the redirect strategy changes your POST request into a GET
                //HttpPost.METHOD_NAME, //In case someone needs to go against the HTTP standard
                HttpHead.METHOD_NAME
            };

            //Handle URLs with not encoded query parameters
            @Override
            protected URI createLocationURI(String location) throws ProtocolException {
                URI retVal = null;
                try {
                    retVal = super.createLocationURI(location);
                } catch (ProtocolException ex) {
                    try {
                        retVal = buildNormalizedURI(new URL(location), true, true, true);
                    } catch (URISyntaxException ex1) {
                        throw ex;
                    } catch (MalformedURLException ex1) {
                        throw ex;
                    }
                }
                return retVal;
            }

            @Override
            public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                return super.isRedirected(request, response, context);
            }

            @Override
            public HttpUriRequest getRedirect(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                return super.getRedirect(request, response, context);
            }

            //Copied from LaxRedirectStrategy, available in HttpClient > 4.2
            @Override
            protected boolean isRedirectable(final String method) {
                for (final String m : CUSTOM_REDIRECT_METHODS) {
                    if (m.equalsIgnoreCase(method)) {
                        return true;
                    }
                }
                return false;
            }
        };

        return customRedirectStrategy;
    }

    private static URI buildNormalizedURI(URL url,
            boolean includePath,
            boolean includeQuery,
            boolean includeFragment) throws URISyntaxException {
        URI retVal = null;

        if (url != null) {
            try {
                String proto = url.getProtocol();
                String userInfo = url.getUserInfo();
                String host = url.getHost();
                int port = (url.getPort() == 80) ? -1 : url.getPort(); // HttpClient fails for some urls where port 80 is explicitly written
                String path = url.getPath();
                String query = url.getQuery();
                String fragment = url.getRef(); // fragment
                if (path != null) {
                    // If the path contains some encoded characters, we cannot play it nicely
                    // Some cases cannot be decoded and encoded back again by the URI multi-arg constructor without problems
                    // for example a URL that contains a URL as a subpath

                    String decodedPath = URLDecoder.decode(path, "UTF-8");
                    if (!path.equals(decodedPath)) {
                        String newPath = includePath ? path : "";
                        if (query != null) {
                            newPath += includeQuery ? "?" + query : "";
                        }
                        if (fragment != null) {
                            newPath += includeFragment ? "#" + fragment : ""; // fragment
                        }

                        try {
                            URL rebuiltUrl = new URL(proto, host, port, newPath);
                            retVal = rebuiltUrl.toURI();
                        } catch (MalformedURLException ex) {
                            ex.printStackTrace();
                        }
                    } else {
                        retVal = new URI(
                                proto,
                                userInfo,
                                host,
                                port,
                                includePath ? path : null,
                                includeQuery ? query : null,
                                includeFragment ? fragment : null
                        );
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        return retVal;
        /*
        rfc3986.txt
        2.2.  Reserved Characters
        URIs include components and subcomponents that are delimited by
        characters in the "reserved" set.  These characters are called
        "reserved" because they may (or may not) be defined as delimiters by
        the generic syntax, by each scheme-specific syntax, or by the
        implementation-specific syntax of a URI's dereferencing algorithm.
        If data for a URI component would conflict with a reserved
        character's purpose as a delimiter, then the conflicting data must be
        percent-encoded before the URI is formed.
         */
    }

    //DART Implement our custom strategy since the default strategy is too optimistic
    //DART quoting the HttpClient documentation: "If the Keep-Alive header is not present in the response, HttpClient assumes the connection can be kept alive indefinitely."
    private static ConnectionKeepAliveStrategy createCustomHttpKeepAliveStrategy() {
        //DART using lambda expression
        ConnectionKeepAliveStrategy kaStrategy = (HttpResponse response, HttpContext context) -> {
            long resTimeout = DEFAULT_KEEP_ALIVE_TIMEOUT_SEC; // value in sec
            HeaderElementIterator heIt = new BasicHeaderElementIterator(response.headerIterator(HTTP.CONN_KEEP_ALIVE));
            while (heIt.hasNext()) {
                HeaderElement he = heIt.nextElement();
                String heParam = he.getName();
                String heValue = he.getValue();
                //DART honor the value in the header
                if (heValue != null && "timeout".equalsIgnoreCase(heParam)) {
                    try {
                        resTimeout = Long.parseLong(heValue); // value in sec
                        break; // only if successfully parsed
                    } catch (NumberFormatException ignoredEx) {
                    }
                }
            }
            //DART return value in millisec
            return resTimeout * 1000;
        };
        return kaStrategy;
    }

}
