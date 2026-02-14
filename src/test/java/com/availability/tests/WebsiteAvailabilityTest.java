package com.availability.tests;

import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WebsiteAvailabilityTest {

    @Test
    void checkWebsiteAvailability() throws Exception {

        FileInputStream fis = new FileInputStream("data/urls.xlsx");
        Workbook workbook = WorkbookFactory.create(fis);
        Sheet sheet = workbook.getSheetAt(0);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        List<String> failedUrls = new ArrayList<>();

        for (Row row : sheet) {

            if (row.getRowNum() == 0) continue;
            if (row.getCell(0) == null) continue;

            String url = row.getCell(0).getStringCellValue().trim();
            if (url.isEmpty()) continue;

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                failedUrls.add(url + " | Invalid URL format");
                continue;
            }

            int maxRetries = 3;
            int attempt = 0;
            boolean success = false;
            int statusCode = -1;

            while (attempt < maxRetries) {
                attempt++;
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();

                    HttpResponse<Void> response =
                            client.send(request, HttpResponse.BodyHandlers.discarding());

                    statusCode = response.statusCode();

                    if (statusCode < 400) {
                        success = true;
                        break;
                    }

                } catch (Exception ignored) {
                }

                Thread.sleep(2000);
            }

            if (!success) {
                failedUrls.add(url + " | Status: " + statusCode);
            }
        }

        workbook.close();
        fis.close();

        // 🚨 FINAL JENKINS-SAFE ALERT SECTION
        if (!failedUrls.isEmpty()) {

            StringBuilder failureMessage = new StringBuilder();
            StringBuilder fileContent = new StringBuilder();

            fileContent.append("WEBSITE DOWN ALERT\n\n");

            for (String failed : failedUrls) {
                String line = "DOWN | " + failed;
                failureMessage.append(line).append("\n");
                fileContent.append(line).append("\n");
            }

            fileContent.append("\nChecked at: ").append(LocalDateTime.now());

            // ✅ Write to workspace file for Jenkins Email-ext
            Files.write(
                    Paths.get("failure-details.txt"),
                    fileContent.toString().getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );

            Assertions.fail(failureMessage.toString());

        } else {

            System.out.println("\n=====================================");
            System.out.println("✅ ALL WEBSITES ARE UP");
            System.out.println("Checked at: " + LocalDateTime.now());
            System.out.println("=====================================\n");
        }
    }
}
