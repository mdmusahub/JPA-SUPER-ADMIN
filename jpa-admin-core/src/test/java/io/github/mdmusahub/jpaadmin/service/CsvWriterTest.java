package io.github.mdmusahub.jpaadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The CSV rules (Phase 11, SEC-10, D-105): empty cells, quoting, the formula guard for each of the six first characters, the
 * order of the two rules, control characters, line endings and the byte order mark.
 */
class CsvWriterTest {

    private static String row(String... cells) throws IOException {
        StringWriter out = new StringWriter();
        new CsvWriter(out).row(Arrays.asList(cells));
        return out.toString();
    }

    // ---- empty cells ----------------------------------------------------------------------------------------------------

    @Test
    void nullAndEmptyAreEmptyCellsWithNoQuotes() throws IOException {
        assertThat(row("a", null, "", "b")).isEqualTo("a,,,b\r\n");
    }

    @Test
    void aCellMadeOnlyOfDroppedControlCharactersIsEmpty() {
        assertThat(CsvWriter.cell("\u0000\u0007\u001b")).isEmpty();
    }

    // ---- quoting ----------------------------------------------------------------------------------------------------------

    @Test
    void aPlainCellIsNotQuoted() {
        assertThat(CsvWriter.cell("plain text 123 ünïcode")).isEqualTo("plain text 123 ünïcode");
    }

    @Test
    void aCommaForcesQuotes() throws IOException {
        assertThat(CsvWriter.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(row("x", "a,b", "y")).isEqualTo("x,\"a,b\",y\r\n");
    }

    @Test
    void aQuoteForcesQuotesAndIsDoubled() {
        assertThat(CsvWriter.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.cell("\"")).isEqualTo("\"\"\"\"");
    }

    @Test
    void aLineFeedForcesQuotesAndIsKept() {
        assertThat(CsvWriter.cell("one\ntwo")).isEqualTo("\"one\ntwo\"");
    }

    @Test
    void aCarriageReturnInsideForcesQuotesAndIsKept() {
        assertThat(CsvWriter.cell("one\r\ntwo")).isEqualTo("\"one\r\ntwo\"");
        assertThat(CsvWriter.cell("one\rtwo")).isEqualTo("\"one\rtwo\"");
    }

    @Test
    void aTabInsideDoesNotNeedQuotes() {
        assertThat(CsvWriter.cell("a\tb")).isEqualTo("a\tb");
    }

    @Test
    void aSingleQuoteAndSemicolonDoNotNeedQuotes() {
        assertThat(CsvWriter.cell("it's; fine")).isEqualTo("it's; fine");
    }

    @Test
    void rowsEndWithCarriageReturnAndLineFeed() throws IOException {
        StringWriter out = new StringWriter();
        CsvWriter writer = new CsvWriter(out);
        writer.row(List.of("a", "b"));
        writer.row(List.of("c", "d"));
        writer.flush();

        assertThat(out.toString()).isEqualTo("a,b\r\nc,d\r\n");
    }

    @Test
    void theByteOrderMarkIsOneCharacterBeforeTheFirstRow() throws IOException {
        StringWriter out = new StringWriter();
        CsvWriter writer = new CsvWriter(out);
        writer.byteOrderMark();
        writer.row(List.of("a"));

        assertThat(out.toString()).isEqualTo("﻿a\r\n");
    }

    // ---- the formula guard: each of the six characters (SEC-10) -------------------------------------------------------

    @Test
    void aLeadingEqualsSignIsGuarded() {
        assertThat(CsvWriter.cell("=1+1")).isEqualTo("'=1+1");
    }

    @Test
    void aLeadingPlusIsGuarded() {
        assertThat(CsvWriter.cell("+1")).isEqualTo("'+1");
    }

    @Test
    void aLeadingMinusIsGuarded() {
        assertThat(CsvWriter.cell("-1")).isEqualTo("'-1");
    }

    @Test
    void aLeadingAtSignIsGuarded() {
        assertThat(CsvWriter.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void aLeadingTabIsGuarded() {
        assertThat(CsvWriter.cell("\t=cmd")).isEqualTo("'\t=cmd");
    }

    @Test
    void aLeadingCarriageReturnIsGuardedAndThenQuoted() {
        // The carriage return is also a reason to quote, so the guarded cell is wrapped as a whole.
        assertThat(CsvWriter.cell("\r=cmd")).isEqualTo("\"'\r=cmd\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=", "+", "-", "@", "\t", "\r"})
    void eachOfTheSixCharactersAloneIsGuarded(String first) {
        String cell = CsvWriter.cell(first);

        assertThat(cell).contains("'");
        assertThat(cell.replace("\"", "")).startsWith("'" + first);
    }

    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(\"http://x\",\"y\")", "+cmd|' /C calc'!A0", "-2+3", "@A1", "=A1,B1"})
    void theGuardComesBeforeTheQuoting(String raw) {
        String cell = CsvWriter.cell(raw);

        String content = cell.startsWith("\"") ? cell.substring(1, cell.length() - 1).replace("\"\"", "\"") : cell;
        assertThat(content).isEqualTo("'" + raw);
    }

    @Test
    void aGuardedCellWithACommaIsStillQuotedAsAWhole() {
        assertThat(CsvWriter.cell("=A1,B1")).isEqualTo("\"'=A1,B1\"");
    }

    @Test
    void anAlreadyQuotedLookingValueIsStillGuarded() {
        assertThat(CsvWriter.cell("\"=1+1\"")).as("starts with a double quote, not a formula character")
                .isEqualTo("\"\"\"=1+1\"\"\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a=b", "a+b", "a-b", "a@b", "a\tb", " =1", "1-2", "x@y.test"})
    void aFormulaCharacterAnywhereButFirstIsLeftAlone(String raw) {
        assertThat(CsvWriter.cell(raw)).isEqualTo(raw);
    }

    @Test
    void aControlCharacterInFrontCannotHideAFormula() {
        assertThat(CsvWriter.cell("\u0000=1+1")).isEqualTo("'=1+1");
        assertThat(CsvWriter.cell("\u001b[=1")).isEqualTo("[=1");
        assertThat(CsvWriter.cell("\u0000@x")).isEqualTo("'@x");
    }

    @Test
    void aWholeHeaderRowIsGuardedToo() throws IOException {
        assertThat(row("ID", "=Name", "-Price")).isEqualTo("ID,'=Name,'-Price\r\n");
    }

    // ---- control characters -----------------------------------------------------------------------------------------------

    @Test
    void controlCharactersOtherThanTabAndLineBreaksAreDropped() {
        assertThat(CsvWriter.cell("a\u0000b\u0007c\u007fd\u0085e")).isEqualTo("abcde");
    }

    @Test
    void unicodeTextIsKeptAsStored() {
        assertThat(CsvWriter.cell("日本語 ☕ 😀 العربية")).isEqualTo("日本語 ☕ 😀 العربية");
    }
}
