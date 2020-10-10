package tigerworkshop.webapphardwarebridge;

import com.sun.management.OperatingSystemMXBean;
import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tigerworkshop.webapphardwarebridge.interfaces.NotificationListenerInterface;
import tigerworkshop.webapphardwarebridge.models.Config;
import tigerworkshop.webapphardwarebridge.services.ApiServerService;
import tigerworkshop.webapphardwarebridge.services.ConfigService;
import tigerworkshop.webapphardwarebridge.utils.CertificateGenerator;
import tigerworkshop.webapphardwarebridge.utils.TLSUtil;
import tigerworkshop.webapphardwarebridge.websocketservices.CloudProxyClientWebSocketService;
import tigerworkshop.webapphardwarebridge.websocketservices.PrinterWebSocketService;
import tigerworkshop.webapphardwarebridge.websocketservices.SerialWebSocketService;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger("Server");
    private static final Server server = new Server();
    private NotificationListenerInterface notificationListener;
    private BridgeWebSocketServer bridgeWebSocketServer;
    private boolean shouldRestart = false;
    private boolean shouldStop = false;

    public Server() {

    }

    public Server(NotificationListenerInterface notificationListener) {
        this.notificationListener = notificationListener;
    }

    public static void main(String[] args) {
        try {
            JUnique.acquireLock(Constants.APP_ID);
        } catch (AlreadyLockedException e) {
            logger.error(Constants.APP_ID + " already running");
            System.exit(1);
        }

        server.start();
    }

    public void start() {
        while (!shouldStop) {
            shouldRestart = false;

            logger.info("Application Started");
            logger.info("Program Version: " + Constants.VERSION);

            logger.debug("OS Name: " + System.getProperty("os.name"));
            logger.debug("OS Version: " + System.getProperty("os.version"));
            logger.debug("OS Architecture: " + System.getProperty("os.arch"));

            logger.debug("Java Version: " + System.getProperty("java.version"));
            logger.debug("Java Vendor: " + System.getProperty("java.vendor"));

            logger.debug("Available processors (cores): " + Runtime.getRuntime().availableProcessors());
            logger.debug("JVM Maximum memory (bytes): " + Runtime.getRuntime().maxMemory());
            logger.debug("System memory (bytes): " + ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalPhysicalMemorySize());

            ConfigService configService = ConfigService.getInstance();
            Config config = configService.getConfig();

            try {
                // Create WebSocket Server
                bridgeWebSocketServer = new BridgeWebSocketServer(config.getServer().getBind(), config.getServer().getPort());
                bridgeWebSocketServer.setReuseAddr(true);
                bridgeWebSocketServer.setConnectionLostTimeout(3);

                // Add Serial Services
                ArrayList<Config.ConfigSerial> serials = config.getSerials();
                for (Config.ConfigSerial elem : serials) {
                    SerialWebSocketService serialWebSocketService = new SerialWebSocketService(elem.getName(), elem.getKey());
                    serialWebSocketService.setServer(bridgeWebSocketServer);
                    serialWebSocketService.start();
                }

                // Add Printer Service
                PrinterWebSocketService printerWebSocketService = new PrinterWebSocketService();
                printerWebSocketService.setServer(bridgeWebSocketServer);
                printerWebSocketService.setNotificationListener(notificationListener);
                printerWebSocketService.start();

                // Add Cloud Proxy Client Service
                if (config.getCloudProxy().getEnabled()) {
                    CloudProxyClientWebSocketService cloudProxyClientWebSocketService = new CloudProxyClientWebSocketService();
                    cloudProxyClientWebSocketService.setServer(bridgeWebSocketServer);
                    cloudProxyClientWebSocketService.start();
                }

                // WSS/TLS Options
                if (config.getServer().getTlsEnabled()) {
                    if (config.getServer().getTlsSelfSigned()) {
                        logger.info("TLS Enabled with self-signed certificate");
                        CertificateGenerator.generateSelfSignedCertificate(config.getServer().getAddress(), config.getServer().getTlsCert(), config.getServer().getTlsKey());
                        logger.info("For first time setup, open in browser and trust the certificate: " + config.getWebSocketUri().replace("wss", "https"));
                    }

                    bridgeWebSocketServer.setWebSocketFactory(TLSUtil.getSecureFactory(config.getServer().getTlsCert(), config.getServer().getTlsKey(), config.getServer().getTlsCaBundle()));
                }

                // API/HTTP Configurator
                if (config.getApi().getEnabled()) {
                    ApiServerService apiServer = new ApiServerService();
                    apiServer.start();
                }

                // Start WebSocket Server
                bridgeWebSocketServer.start();

                logger.info("WebSocket started on " + config.getWebSocketUri());

                while (!shouldRestart && !shouldStop) {
                    Thread.sleep(100);
                }

                bridgeWebSocketServer.close();
                bridgeWebSocketServer.stop();
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    public void stop() {
        shouldStop = true;
    }

    public void restart() {
        shouldRestart = true;
    }
}
