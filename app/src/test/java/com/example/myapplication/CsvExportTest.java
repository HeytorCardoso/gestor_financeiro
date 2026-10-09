package com.example.myapplication;

import org.junit.Test;
import java.io.StringWriter;
import static org.junit.Assert.*;

public class CsvExportTest {
    @Test public void escapesQuotesSeparatorsAndMultilineText() {
        assertEquals("\"Loja; \"\"Centro\"\"\nCafé\"",CsvExport.cell("Loja; \"Centro\"\nCafé"));
    }
    @Test public void guardsSpreadsheetFormulasIncludingLeadingWhitespace() {
        assertEquals("\"'=1+1\"",CsvExport.cell("=1+1"));
        assertEquals("\"'  @SUM(A1)\"",CsvExport.cell("  @SUM(A1)"));
        assertEquals("\"'-1+2\"",CsvExport.cell("-1+2"));
    }
    @Test public void amountsUseExactCentsAndRemainNumeric() {
        assertEquals("-10,01",CsvExport.amount(-1001));assertEquals("0,01",CsvExport.amount(1));
        assertEquals("\"-10,01\"",CsvExport.cell(CsvExport.amount(-1001)));
    }
    @Test public void writesPortugueseCsvWithWindowsLineEndings() throws Exception {
        StringWriter writer=new StringWriter();CsvExport.row(writer,"Descrição","Categoria");CsvExport.row(writer,"Café","Alimentação");
        assertEquals("\"Descrição\";\"Categoria\"\r\n\"Café\";\"Alimentação\"\r\n",writer.toString());
    }
}
