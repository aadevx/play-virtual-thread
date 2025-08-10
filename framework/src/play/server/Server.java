package play.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import play.Logger;
import play.Play;
import play.Play.Mode;
import play.libs.IO;
import play.server.http2.Http2ServerInitializer;

import javax.net.ssl.KeyManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.Security;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static int httpPort;
    public static int httpsPort;

    public static final String PID_FILE = "server.pid";
    public static final String certificateKeyFile = Play.configuration.getProperty("certificate.key.file", "conf/host.key");
    public static final String certificateFile = Play.configuration.getProperty("certificate.file", "conf/host.cert");
    public static final String keyStoreAlg = Play.configuration.getProperty("keystore.algorithm", "JKS");
    public static final String keyStoreFile = Play.configuration.getProperty("keystore.file", "conf/certificate.jks");
    public static final String keyStorePass = Play.configuration.getProperty("keystore.password", "secret");

    public Server(String[] args) {

        System.setProperty("file.encoding", "utf-8");
        Properties p = Play.configuration;

        httpPort = Integer.parseInt(getOpt(args, "http.port", p.getProperty("http.port", "-1")));
        httpsPort = Integer.parseInt(getOpt(args, "https.port", p.getProperty("https.port", "-1")));

        if (httpPort == -1 && httpsPort == -1) {
            httpPort = 9000;
        }

        if (httpPort == httpsPort) {
            Logger.error("Could not bind on https and http on the same port " + httpPort);
            Play.fatalServerErrorOccurred();
        }

        InetAddress address = null;
        InetAddress secureAddress = null;
        try {
            if (p.getProperty("http.address") != null) {
                address = InetAddress.getByName(p.getProperty("http.address"));
            } else if (System.getProperties().containsKey("http.address")) {
                address = InetAddress.getByName(System.getProperty("http.address"));
            }

        } catch (Exception e) {
            Logger.error(e, "Could not understand http.address");
            Play.fatalServerErrorOccurred();
        }
        try {
            if (p.getProperty("https.address") != null) {
                secureAddress = InetAddress.getByName(p.getProperty("https.address"));
            } else if (System.getProperties().containsKey("https.address")) {
                secureAddress = InetAddress.getByName(System.getProperty("https.address"));
            }
        } catch (Exception e) {
            Logger.error(e, "Could not understand https.address");
            Play.fatalServerErrorOccurred();
        }
        SslContext sslCtx = null;
        if (httpsPort != -1) { // SSL

            try {
                File hostKey = Play.getFile(certificateKeyFile);
                File hostCert = Play.getFile(certificateFile);
                if (hostKey.exists() && hostCert.exists()) {
                    sslCtx = SslContextBuilder.forServer(hostCert, hostKey).build();
                } else {
                    // Try to load it from the keystore
                    KeyStore ks = KeyStore.getInstance(keyStoreAlg);
                    // Load the file from the conf
                    char[] certificatePassword = keyStorePass.toCharArray();
                    InputStream fis = new FileInputStream(Play.getFile(keyStoreFile));
                    ks.load(fis, certificatePassword);
                    fis.close();
                    // Set up key manager factory to use our key store
                    String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
                    if (algorithm == null) {
                        algorithm = "SunX509";
                    }
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
                    kmf.init(ks, certificatePassword);
                    sslCtx = SslContextBuilder.forServer(kmf).build();
                }
               /* SelfSignedCertificate ssc = new SelfSignedCertificate();
                sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();*/
            } catch (Exception e) {
                Logger.error(e, e.getMessage());
            }
        }
        NettyTransport transport = NettyTransport.transport(getClass().getClassLoader());

        /** Acceptor event-loop */
        EventLoopGroup bossGroup = transport.createEventLoop(1, "acceptor", 50);

        /** Event loop: processing connections, parsing messages and doing engine's internal work */
        EventLoopGroup workerGroup = transport.createEventLoop(0, "eventloop", 100);
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        try {
            ServerBootstrap bootstrap = transport.configure(bossGroup, workerGroup);
            bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_REUSEADDR, true);
//            bootstrap.handler(new LoggingHandler(LogLevel.INFO));
            if(Play.configuration.getProperty("http.version", "1.1").startsWith("2")) {
                Logger.info("handler http 2");
                bootstrap.childHandler(new Http2ServerInitializer(sslCtx, executorService));
            }
            else {
                bootstrap.childHandler(new HttpServerInitializer(sslCtx, executorService)); // default handler http 1.1
            }
            Channel ch = null;
            if (httpPort != -1) {
                ch = bootstrap.bind(new InetSocketAddress(address, httpPort)).sync().channel();
                if (address == null) {
                    if (Play.mode == Mode.DEV)
                        Logger.info("Listening for HTTP on port %s (Waiting a first request to start) ...", httpPort);
                    else
                        Logger.info("Listening for HTTP on port %s ...", httpPort);
                } else {
                    if (Play.mode == Mode.DEV)
                        Logger.info("Listening for HTTP at %2$s:%1$s (Waiting a first request to start) ...", httpPort, address);
                    else
                        Logger.info("Listening for HTTP at %2$s:%1$s  ...", httpPort, address);
                }
            }
            if (httpsPort != -1) {
                ch = bootstrap.bind(new InetSocketAddress(secureAddress, httpsPort)).sync().channel();
                if (secureAddress == null) {
                    if (Play.mode == Mode.DEV)
                        Logger.info("Listening for HTTPS on port %s (Waiting a first request to start) ...", httpsPort);
                    else
                        Logger.info("Listening for HTTPS on port %s ...", httpsPort);
                } else {
                    if (Play.mode == Mode.DEV)
                        Logger.info("Listening for HTTPS at %2$s:%1$s (Waiting a first request to start) ...", httpsPort, secureAddress);
                    else
                        Logger.info("Listening for HTTPS at %2$s:%1$s  ...", httpsPort, secureAddress);
                }
            }
            ch.closeFuture().sync();
            if (Play.mode == Mode.DEV || Play.runningInTestMode()) {
                // print this line to STDOUT - not using logger, so auto test runner will not block if logger is misconfigured (see #1222)
                System.out.println("~ Server is up and running");
            }
        } catch (Exception e) {
            Logger.error(e,"Could not bind on port " + httpPort);
            Play.fatalServerErrorOccurred();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }

    private String getOpt(String[] args, String arg, String defaultValue) {
        String s = "--" + arg + "=";
        for (String a : args) {
            if (a.startsWith(s)) {
                return a.substring(s.length());
            }
        }
        return defaultValue;
    }

    private static void writePID(File root) {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String pathFilePid = System.getProperty("pidfile.path", root.getAbsolutePath()+File.separator+PID_FILE);
        File pidfile = new File(pathFilePid);
        if (pidfile.exists()) {
            throw new RuntimeException("The " + PID_FILE + " already exists. Is the server already running?");
        }
        IO.write(pid.getBytes(), pidfile);
    }

    public static void main(String[] args) throws Exception {
        try {
            File root = new File(System.getProperty("application.path", "."));
            if (System.getProperty("precompiled", "false").equals("true")) {
                Play.usePrecompiled = true;
            }
            if (System.getProperty("writepid", "false").equals("true")) {
                writePID(root);
            }

            Play.init(root, System.getProperty("play.id", ""));

            if (System.getProperty("precompile") == null) {
                new Server(args);
            } else {
                Logger.info("Done.");
            }
        }
        catch (Throwable e) {
            Logger.fatal(e, "Failed to start");
            System.exit(1);
        }
    }
}
