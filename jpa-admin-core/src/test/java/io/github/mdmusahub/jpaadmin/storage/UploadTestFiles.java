package io.github.mdmusahub.jpaadmin.storage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;

/**
 * Real small files, made here with the JDK's own image writer and by hand, never downloaded: a PNG, a JPEG, a GIF, a WebP and a
 * PDF that the signature table must recognise, and the disguised and hidden-markup files it must refuse.
 */
public final class UploadTestFiles {

    private UploadTestFiles() {
    }

    private static byte[] image(String format) {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                image.setRGB(x, y, 0x336699 + x * 16 + y);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("no writer for " + format);
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return out.toByteArray();
    }

    /** A real 4 by 4 PNG. */
    public static byte[] png() {
        return image("png");
    }

    /** A real 4 by 4 JPEG. */
    public static byte[] jpeg() {
        return image("jpg");
    }

    /** A real 4 by 4 GIF. */
    public static byte[] gif() {
        return image("gif");
    }

    /** The first 30 bytes of a lossy WebP file: the container header and a chunk name. Enough for a signature, not for a decoder. */
    public static byte[] webp() {
        byte[] body = "RIFF\u0016\u0000\u0000\u0000WEBPVP8 \n\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000"
                .getBytes(StandardCharsets.ISO_8859_1);
        return body;
    }

    /** A tiny but well-formed PDF. */
    public static byte[] pdf() {
        return ("%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n2 0 obj<</Type/Pages/Count 0/Kids[]>>endobj\n"
                + "trailer<</Root 1 0 R>>\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1);
    }

    /** What a Windows program starts with, named {@code .png} by the person who sends it. */
    public static byte[] executable() {
        byte[] bytes = new byte[256];
        bytes[0] = 'M';
        bytes[1] = 'Z';
        Arrays.fill(bytes, 2, bytes.length, (byte) 0x90);
        return bytes;
    }

    /** A page with a script, named {@code .jpg} by the person who sends it. */
    public static byte[] html() {
        return "<!DOCTYPE html><html><body><script>alert(document.cookie)</script></body></html>"
                .getBytes(StandardCharsets.UTF_8);
    }

    /** A real PNG with a page and script after its end: valid to an image decoder, a page to anything that sniffs. */
    public static byte[] polyglotPng() {
        return append(png(), "<script>alert(1)</script>");
    }

    public static byte[] append(byte[] file, String tail) {
        byte[] extra = tail.getBytes(StandardCharsets.UTF_8);
        byte[] all = Arrays.copyOf(file, file.length + extra.length);
        System.arraycopy(extra, 0, all, file.length, extra.length);
        return all;
    }

    /** The signature of a PNG followed by {@code zeros} zero bytes: the right size, not a real image. For size checks. */
    public static byte[] pngOfSize(int total) {
        byte[] bytes = new byte[total];
        byte[] head = png();
        System.arraycopy(head, 0, bytes, 0, Math.min(head.length, total));
        return bytes;
    }
}
