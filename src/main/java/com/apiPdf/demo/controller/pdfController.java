    package com.apiPdf.demo.controller;
    import com.apiPdf.demo.RelatorioPdf;
    import org.springframework.http.HttpHeaders;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.MediaType;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.bind.annotation.CookieValue;
    import org.springframework.web.bind.annotation.GetMapping;
    import org.springframework.web.bind.annotation.RequestMapping;
    import org.springframework.web.bind.annotation.RequestParam;
    import org.springframework.web.bind.annotation.RestController;
    import org.springframework.web.bind.annotation.CrossOrigin;

    @RestController
    @CrossOrigin(origins = "http://localhost:3333", allowCredentials = "true")
    @RequestMapping("/relatorio")

    public class pdfController {

        @CrossOrigin(
                origins = "http://localhost:3333",
                allowCredentials = "true",
                exposedHeaders = "Content-Disposition"
        )

        // GET /relatorio/pdf?servidorId=1&componenteId=2&servidor=SRV-01&componente=cpu
            // O token do usuário chega no cookie "token" (repassado pelo Node) e segue para a API de logs.

            @GetMapping("/pdf")
            public ResponseEntity<?> pdf(@RequestParam String servidorId,
                                         @RequestParam String componenteId,
                                         @RequestParam String servidor,
                                         @RequestParam String componente,
                                         @CookieValue(name = "access_token", required = false) String token) {

                if (token == null || token.isBlank()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token ausente.");
                }

                if (servidorId == null || servidorId.isBlank()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("servidorId ausente.");
                }

                if (componenteId == null || componenteId.isBlank()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("componenteId ausente.");
                }

                if (servidor == null || servidor.isBlank()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("servidor ausente.");
                }

                if (componente == null || componente.isBlank()) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("componente ausente.");
                }

                try {
                    byte[] pdf = RelatorioPdf.gerar(servidorId, componenteId, servidor, componente, token);
                    String nomeArquivo = "relatorio-" + servidor.replaceAll("[^a-zA-Z0-9._-]", "_") + ".pdf";

                    return ResponseEntity.ok()
                            .contentType(MediaType.APPLICATION_PDF)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nomeArquivo + "\"")
                            .body(pdf);

                } catch (Exception e) {
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body("Não foi possível gerar o relatório: " + e.getMessage());
                }
            }
        }

