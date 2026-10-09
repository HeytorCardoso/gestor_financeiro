package com.example.myapplication;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;

/** CSV separado por ponto e vírgula, com proteção de células interpretadas como fórmulas. */
public final class CsvExport {
    private CsvExport() {}
    public static String cell(String value) {
        String stripped=value.trim();
        if(!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0 && !stripped.matches("-?[0-9]+(,[0-9]{1,2})?")) value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
    public static String amount(long cents) {return BigDecimal.valueOf(cents,2).toPlainString().replace('.',',');}
    public static void row(Writer writer,String... values) throws IOException {
        for(int i=0;i<values.length;i++) {if(i>0)writer.write(';');writer.write(cell(values[i]));}
        writer.write("\r\n");
    }
}
