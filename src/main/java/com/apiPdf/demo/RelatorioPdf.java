package com.apiPdf.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class RelatorioPdf {

    private static final String BASE_URL = "http://localhost:3333/api/log";
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Color AZUL = new Color(14, 90, 167);
    private static final Color CINZA = new Color(240, 243, 246);
    private static final Font F_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, AZUL);
    private static final Font F_SUB = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.DARK_GRAY);
    private static final Font F_TEXTO = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font F_HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

    static JsonNode api(String rota, Map<String, String> params, String token) throws Exception {

        StringBuilder queryString = new StringBuilder();

        for (var entry : params.entrySet()) {
            if (!queryString.isEmpty()) queryString.append('&');
            queryString.append(entry.getKey()).append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/" + rota + "?" + queryString))
                .header("Cookie", "access_token=" + token)
                .GET()
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException("Erro " + res.statusCode() + " em /" + rota + ": " + res.body());
        }
        return JSON.readTree(res.body()).get("log");
    }

    static String statusDe(String string) {
        String status = string == null ? "" : string.toLowerCase();
        if (status.contains("crit")) return "Crítico";
        if (status.contains("med") || status.contains("atenc") || status.contains("atenç")) return "Atenção";
        if (status.contains("baixo") || status.contains("normal") || status.equals("ok")) return "Normal";
        return "Inativo";
    }

    static Color corStatus(String status) {
        return switch (status) {
            case "Crítico" -> new Color(255, 77, 109);
            case "Atenção" -> new Color(228, 175, 68);
            case "Normal" -> new Color(40, 199, 111);
            default -> new Color(107, 119, 133);
        };
    }

    public static byte[] gerar(String servidorId, String componenteId,
                               String nomeServidor, String componente, String token) throws Exception {

        JsonNode historico = api("historico", Map.of(
                "servidor_id", servidorId, "componente_id", componenteId, "dias", "7"), token);
        JsonNode logs = api("listar-logs", Map.of(
                "servidor_id", servidorId, "componente_id", componenteId, "dias", "7"), token);

        double pico = 0.0,
                soma = 0.0,
                limite = 80.0;

        int acima = 0,
                qtd = 0;

        for (JsonNode res : historico) {
            double uso = res.path("uso").asDouble(0);
            if (qtd == 0 && res.hasNonNull("limite_max")) limite = res.get("limite_max").asDouble(80);
            pico = Math.max(pico, uso);
            soma += uso;
            qtd++;
        }

        for (JsonNode res : historico) {
            if (res.path("uso").asDouble(0) > limite) acima++;
        }

        double media = qtd == 0 ? 0 : soma / qtd;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 50, 40);
        PdfWriter.getInstance(doc, baos);
        doc.open();

        doc.add(new Paragraph("RoadVizor | Relatório", F_TITULO));
        doc.add(new Paragraph("Servidor: " + nomeServidor + "   •   Componente: " + componente.toUpperCase(), F_SUB));
        doc.add(new Paragraph("Gerado em " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), F_TEXTO));
        doc.add(new Paragraph(" "));

        doc.add(new Paragraph("Resumo dos últimos 7 dias", F_SUB));
        String resumo = qtd == 0
                ? "Sem leituras de uso nos últimos 7 dias para este servidor."
                : String.format("Pico de %.0f%%, média de %.0f%% e %d de %d dia(s) com pico acima do limite de %.0f%%.",
                pico, media, acima, qtd, limite);
        doc.add(new Paragraph(resumo, F_TEXTO));
        doc.add(new Paragraph(" "));

        doc.add(new Paragraph("Uso diário", F_SUB));
        PdfPTable tituloHistorico = new PdfPTable(new float[]{2, 1});
        tituloHistorico.setWidthPercentage(100);
        tituloHistorico.setSpacingBefore(6);
        cabecalho(tituloHistorico, "Dia", "Uso (%)");

        for (JsonNode hist : historico) {
            celula(tituloHistorico, hist.path("dia").asText("--"));
            celula(tituloHistorico, String.format("%.0f%%", hist.path("uso").asDouble(0)));
        }

        doc.add(tituloHistorico);
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Logs recentes", F_SUB));

        PdfPTable tituloLogs = new PdfPTable(new float[]{2, 1.2f, 1, 5});
        tituloLogs.setWidthPercentage(100);
        tituloLogs.setSpacingBefore(6);

        cabecalho(tituloLogs, "Data/hora", "Status", "Uso", "Descrição");

        for (JsonNode log : logs) {
            String status = statusDe(log.path("status").asText());
            celula(tituloLogs, log.path("data_hora").asText("--"));

            PdfPCell cedula = new PdfPCell(new Phrase(status,
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, corStatus(status))));
            cedula.setPadding(5);
            tituloLogs.addCell(cedula);

            celula(tituloLogs, log.hasNonNull("valor_uso")
                    ? Math.round(log.get("valor_uso").asDouble()) + "%" : "--");
            celula(tituloLogs, log.path("titulo").asText(""));
        }

        doc.add(tituloLogs);

        doc.close();
        return baos.toByteArray();
    }

    private static void cabecalho(PdfPTable table, String... titulos) {
        for (String string : titulos) {
            PdfPCell cedula = new PdfPCell(new Phrase(string, F_HEAD));
            cedula.setBackgroundColor(AZUL);
            cedula.setPadding(5);
            cedula.setHorizontalAlignment(Element.ALIGN_LEFT);
            table.addCell(cedula);
        }
    }

    private static void celula(PdfPTable table, String texto) {
        PdfPCell cedula = new PdfPCell(new Phrase(texto, F_TEXTO));
        cedula.setBorderColor(CINZA);
        cedula.setPadding(5);
        table.addCell(cedula);
    }
}