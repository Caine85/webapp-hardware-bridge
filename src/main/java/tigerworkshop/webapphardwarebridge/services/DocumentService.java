package tigerworkshop.webapphardwarebridge.services;

import org.bouncycastle.util.encoders.Base64;
import org.slf4j.LoggerFactory;
import tigerworkshop.webapphardwarebridge.responses.PrintDocument;
import tigerworkshop.webapphardwarebridge.utils.DownloadUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class DocumentService {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(DocumentService.class.getName());
    private static final DocumentService instance = new DocumentService();
    private static final ConfigService configService = ConfigService.getInstance();

    private DocumentService() {
        File directory = new File(configService.getConfig().getDownloader().getPath());
        if (!directory.exists()) {
            directory.mkdir();
        }
    }

    public static DocumentService getInstance() {
        return instance;
    }

    public static void extract(String base64, String urlString) throws Exception {
        byte[] bytes = Base64.decode(base64);

        try (OutputStream stream = new FileOutputStream(getPathFromUrl(urlString))) {
            stream.write(bytes);
        }
    }

    public static void download(String urlString) throws Exception {
        DownloadUtil.file(urlString, getPathFromUrl(urlString), true, configService.getConfig().getDownloader().getIgnoreTLSCertificateError(), configService.getConfig().getDownloader().getTimeout());
    }

    public static File getFileFromUrl(String urlString) {
        return new File(getPathFromUrl(urlString));
    }

    public static void deleteFileFromUrl(String urlString) {
        getFileFromUrl(urlString).delete();
    }

    public static String getPathFromUrl(String urlString) {
        urlString = urlString.replace(" ", "%20");
        String filename = urlString.substring(urlString.lastIndexOf("/") + 1);
        return configService.getConfig().getDownloader().getPath() + filename;
    }

    public void prepareDocument(PrintDocument printDocument) throws Exception {
        if (printDocument.getRaw_content() != null && !printDocument.getRaw_content().isEmpty()) {
            return;
        }

        if (printDocument.getUrl() == null && printDocument.getFile_content() == null) {
            throw new Exception("URL is null");
        }

        if (printDocument.getFile_content() != null) {
            extract(printDocument.getFile_content(), printDocument.getUrl());
        } else {
            download(printDocument.getUrl());
        }
    }
}
