package io.github.mdmusahub.jpaadmin.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The one pass over a file: the bytes that really arrive, and markup that has no business in an image or a PDF. */
class ContentScanTest {

    private static ContentScan.Result scan(byte[] bytes, long limit) throws IOException {
        return ContentScan.scan(new ByteArrayInputStream(bytes), limit);
    }

    private static byte[] text(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void anHonestFileIsCountedAndClean() throws IOException {
        ContentScan.Result result = scan(UploadTestFiles.png(), 1_000_000);

        assertThat(result.bytes()).isEqualTo(UploadTestFiles.png().length);
        assertThat(result.markup()).isFalse();
        assertThat(result.tooLarge()).isFalse();
    }

    @Test
    void anEmptyFileIsZeroBytes() throws IOException {
        assertThat(scan(new byte[0], 10).bytes()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<script>", "<SCRIPT", "<ScRiPt src=x", "<html>", "<HTML", "<?php echo 1;", "<?PHP", "<iframe src=x",
            "<IFRAME", "<svg onload=x>", "<SVG", "<!doctype html>", "<!DOCTYPE HTML>", "<!DocType Html"})
    void everyMarkerIsFoundInAnyLetterCase(String marker) throws IOException {
        assertThat(scan(text("abc" + marker + "def"), 1000).markup()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<scrip", "<scrip>", "<htm>", "<?ph", "<?xml version", "< script", "<\u0000script",
            "script>", "<b>", "<a href=x>", "<!doctyp", "<!doctype xml"})
    void textThatOnlyLooksLikeAMarkerIsNot(String text) throws IOException {
        assertThat(scan(text("abc" + text + "def"), 1000).markup()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 65_522, 65_523, 65_529, 65_530, 65_535, 65_536, 65_537, 131_064, 131_070, 131_072})
    void aMarkerIsFoundAtAnyOffsetIncludingTheJoinBetweenTwoReads(int offset) throws IOException {
        byte[] file = new byte[140_000];
        byte[] marker = text("<!doctype html");
        System.arraycopy(marker, 0, file, offset, marker.length);

        assertThat(scan(file, 1_000_000).markup()).isTrue();
    }

    @Test
    void aMarkerSplitAcrossReadsOfAnyShapeIsFound() throws IOException {
        // A stream that hands out one byte at a time exercises the carry between reads in the worst way.
        byte[] file = text("0123456789<!doctype html>tail");
        InputStream oneByte = new InputStream() {
            private int position;

            @Override
            public int read() {
                return position < file.length ? file[position++] & 0xFF : -1;
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                int value = read();
                if (value < 0) {
                    return -1;
                }
                buffer[offset] = (byte) value;
                return 1;
            }
        };

        assertThat(ContentScan.scan(oneByte, 1000).markup()).isTrue();
    }

    @Test
    void aFileOverTheLimitStopsAsSoonAsItIsKnownToBeOver() throws IOException {
        byte[] file = new byte[1_000_000];
        InputStream counting = new ByteArrayInputStream(file);

        ContentScan.Result result = ContentScan.scan(counting, 100_000);

        assertThat(result.tooLarge()).isTrue();
        assertThat(result.bytes()).isGreaterThan(100_000).isLessThanOrEqualTo(100_000 + 64 * 1024);
        assertThat(counting.available()).isGreaterThan(800_000);
    }

    @Test
    void aFileExactlyAtTheLimitIsNotTooLarge() throws IOException {
        assertThat(scan(new byte[1000], 1000).tooLarge()).isFalse();
        assertThat(scan(new byte[1001], 1000).tooLarge()).isTrue();
    }
}
