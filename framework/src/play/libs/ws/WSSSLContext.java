package play.libs.ws;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class WSSSLContext {
    public static SSLContext getSslContext(String keyStore, String keyStorePass, Boolean CAValidation) {
        SSLContext sslCTX = null;

        try {
            // Keystore
            InputStream kss = new FileInputStream(keyStore);
            char[] storePass = keyStorePass.toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(kss, storePass);
            kss.close();
            // Keymanager
            char[] certPwd = keyStorePass.toCharArray();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, certPwd);
            KeyManager[] keyManagers = kmf.getKeyManagers();

            // Trustmanager
            TrustManager[] trustManagers = null;
            if (CAValidation) {
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
                tmf.init(ks);
                trustManagers = tmf.getTrustManagers();
            } else {
                trustManagers = new TrustManager[]{
                        new X509TrustManager() {
                            @Override
                            public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }
                        }
                };
            }

            SecureRandom secureRandom = new SecureRandom();

            // SSL context
            sslCTX = SSLContext.getInstance("TLS");
            sslCTX.init(keyManagers, trustManagers, secureRandom);
        } catch (Exception e) {
            throw new RuntimeException("Error setting SSL context " + e);
        }
        return sslCTX;
    }
    
    // accept all certificate
    public static SSLContext getSslContext(){
    	SSLContext context;
		try {
			context = SSLContext.getInstance("SSL");
			context.init(null, trustAllCerts, null);
		} catch (Exception e) {
            throw new RuntimeException("Error setting SSL context " + e);
        }
    	return context;
    }

    public static final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };
}
