package ca.xef5000.quantumcraft.util;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for compressing and decompressing data.
 */
public class CompressionUtil {

    /**
     * Compresses a byte array using GZIP compression.
     *
     * @param data The data to compress
     * @return The compressed data
     * @throws IOException If compression fails
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            return new byte[0];
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            
            gzipOut.write(data);
            gzipOut.finish();
            return baos.toByteArray();
        }
    }

    /**
     * Decompresses a GZIP compressed byte array.
     *
     * @param compressedData The compressed data
     * @return The decompressed data
     * @throws IOException If decompression fails
     */
    public static byte[] decompress(byte[] compressedData) throws IOException {
        if (compressedData == null || compressedData.length == 0) {
            return new byte[0];
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    /**
     * Calculates the compression ratio.
     *
     * @param originalSize   The original size in bytes
     * @param compressedSize The compressed size in bytes
     * @return The compression ratio as a percentage
     */
    public static double getCompressionRatio(long originalSize, long compressedSize) {
        if (originalSize == 0) return 0.0;
        return ((double) compressedSize / originalSize) * 100.0;
    }

    /**
     * Formats a byte size into a human-readable string.
     *
     * @param bytes The size in bytes
     * @return A formatted string (e.g., "1.5 MB")
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}
